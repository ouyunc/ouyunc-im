package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.GroupMessagePushModeEnum;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushFailures;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import com.ouyunc.message.processor.http.push.IngressPacketHelper;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.support.MessageIndexScope;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送：群聊投递（模拟用户，与 {@link com.ouyunc.message.processor.GroupMessageBiProcessor} 对齐）。
 */
public final class GroupHttpPushDeliveryStrategy implements HttpProcessor {

    public static final GroupHttpPushDeliveryStrategy INSTANCE = new GroupHttpPushDeliveryStrategy();

    private static final Logger log = LoggerFactory.getLogger(GroupHttpPushDeliveryStrategy.class);

    private GroupHttpPushDeliveryStrategy() {
    }

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void preProcess(Packet packet) throws HttpPipelineException {
        HttpPushValidatorChain.verifyGroup(packet);
        HttpPushDeliverySupport.requireValidMessageRef(packet);
        Set<String> groupUserIdentitySet = DefaultRepository.INSTANCE.groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群成员");
        }
        boolean skipSenderMembership = IngressPacketHelper.isHttpPush(packet)
                && IngressPacketHelper.isSystemLikeSender(packet.getMessage());
        if (!skipSenderMembership && !groupUserIdentitySet.contains(packet.getMessage().getFrom())) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中");
        }
        Set<String> allGroupMembers = new HashSet<>(groupUserIdentitySet);
        allGroupMembers.add(packet.getMessage().getFrom());
        requireValidGroupAt(packet, allGroupMembers);
        // process 复用受理时刻成员集，不再二次全量查询
        HttpPushDeliverySupport.stashGroupMembers(packet, new HashSet<>(groupUserIdentitySet));
    }

    @Override
    public void process(Packet packet) {
        HttpPushDeliverySupport.subscribeDelivery(packet, doProcess(packet));
    }

    private Mono<Boolean> doProcess(Packet packet) {
        Set<String> groupUserIdentitySet = HttpPushDeliverySupport.takeGroupMembers(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("HTTP 推送群组缺少 preProcess 缓存的成员, group={}, packetId={}",
                    packet.getMessage().getTo(), packet.getPacketId());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR,
                    "群成员缓存丢失", packet);
            return Mono.just(false);
        }
        // 投递目标不含发送方；系统代发时 from 可能本就不在集内
        groupUserIdentitySet.remove(packet.getMessage().getFrom());
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return saveThenWithdraw(packet, groupUserIdentitySet);
        }
        return saveAndDeliverChat(packet, groupUserIdentitySet);
    }

    private Mono<Boolean> saveAndDeliverChat(Packet packet, Set<String> groupUserIdentitySet) {
        return DefaultRepository.INSTANCE.reactiveSaveMessage(packet, packet.getMessage().getTo(),
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(saved -> {
                    if (!Boolean.TRUE.equals(saved)) {
                        log.error("HTTP 推送群聊落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "群聊消息写入会话失败", packet);
                        return Mono.just(false);
                    }
                    DefaultRepository.INSTANCE.saveLastMessageForSession(packet.getMessage().getTo(), packet,
                            MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.GROUP,
                                    MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(ignored -> { }, e -> log.warn(
                                    "HTTP 推送更新群聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    pushGroupOnline(packet, groupUserIdentitySet);
                    return Mono.just(true);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送群聊落库异常, packetId={}", packet.getPacketId(), error);
                    HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "群聊持久化异常: " + error.getMessage(), packet);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> saveThenWithdraw(Packet packet, Set<String> groupUserIdentitySet) {
        return DefaultRepository.INSTANCE.reactiveSaveMessage(packet, packet.getMessage().getTo(),
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(saved -> {
                    if (!Boolean.TRUE.equals(saved)) {
                        log.error("HTTP 推送群聊撤回消息落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "群聊撤回消息写入会话失败", packet);
                        return Mono.just(false);
                    }
                    DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.GROUP,
                                    MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(ignored -> { }, e -> log.warn(
                                    "HTTP 推送撤回更新群聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    return handleWithdraw(packet, groupUserIdentitySet);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送群聊撤回落库异常, packetId={}", packet.getPacketId(), error);
                    HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "群聊撤回持久化异常: " + error.getMessage(), packet);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> handleWithdraw(Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        Set<String> leaderOrManagerIdentitySet = DefaultRepository.INSTANCE.groupManagerAndLeaderUsersIdentity(packet);
        boolean leaderOrManager = CollectionUtils.isNotEmpty(leaderOrManagerIdentitySet)
                && leaderOrManagerIdentitySet.contains(packet.getMessage().getFrom());
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadWithdrawTargetPackets(
                                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, !leaderOrManager),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                        packets -> DefaultRepository.INSTANCE.reactiveWithdrawMessage(
                                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, packets),
                        (ctx, packet0) -> {
                            Message msg = packet0.getMessage();
                            if (msg != null && msg.getMetadata() != null) {
                                String appKey = msg.getMetadata().getAppKey();
                                if (StringUtils.isNoneBlank(appKey, sessionId)) {
                                    DefaultRepository.INSTANCE.refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                                }
                            }
                            deliverWithdraw(packet0, groupUserIdentitySet);
                        },
                        HttpPushDeliverySupport::publishExceptionEvent,
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    private Mono<Boolean> handleReadReceipt(Packet packet) {
        String sessionId = packet.getMessage().getTo();
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadValidatedReadReceiptPackets(
                                packet, sessionId, IdentityType.GROUP, false),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                        packets -> DefaultRepository.INSTANCE.reactiveReadReceiptMessage(
                                packet, IdentityType.GROUP,
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                        (ctx, packet0) -> deliverGroupReadReceiptSelfSyncOnly(packet0),
                        HttpPushDeliverySupport::publishExceptionEvent,
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    private static void deliverGroupReadReceiptSelfSyncOnly(Packet packet) {
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, packet.getMessage().getFrom());
    }

    private static void deliverWithdraw(Packet packet, Set<String> groupMembers) {
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, packet.getMessage().getFrom());
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, groupMembers);
    }

    private static void pushGroupOnline(Packet packet, Set<String> groupMembers) {
        Message message = packet.getMessage();
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, message.getFrom());
        GroupMessagePushModeEnum mode = MessageServerContext.serverProperties().getGroupMessagePushMode();
        if (GroupMessagePushModeEnum.PUSH.equals(mode)) {
            deliverToAllGroupMembers(packet, groupMembers);
        } else if (GroupMessagePushModeEnum.PULL.equals(mode)) {
            List<String> atList = message.getAt();
            if (CollectionUtils.isNotEmpty(atList)) {
                deliverToAtMembers(packet, atList, groupMembers);
            }
        } else if (GroupMessagePushModeEnum.PULL_PUSH.equals(mode)) {
            if (groupMembers.size() + NumberConstant.NUMBER_1
                    > MessageServerContext.serverProperties().getGroupMessageThreshold()) {
                List<String> atList = message.getAt();
                if (CollectionUtils.isNotEmpty(atList)) {
                    deliverToAtMembers(packet, atList, groupMembers);
                }
            } else {
                deliverToAllGroupMembers(packet, groupMembers);
            }
        } else {
            log.warn("HTTP 推送暂不支持群消息推送模式: {}", mode);
        }
    }

    private static void deliverToAllGroupMembers(Packet packet, Set<String> groupMembers) {
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, groupMembers);
    }

    private static void deliverToAtMembers(Packet packet, List<String> atList, Set<String> groupMembers) {
        Set<String> targets = AtMentionHelper.resolveDeliveryTargets(atList, groupMembers);
        Message message = packet.getMessage();
        String groupId = message.getTo();
        targets.forEach(member -> MessageDeliveryRouteHelper.deliverGroupMember(packet, groupId, member));
    }

    private static void requireValidGroupAt(Packet packet, Set<String> allGroupMembers)
            throws HttpPipelineException {
        Message message = packet.getMessage();
        List<String> at = message.getAt();
        if (CollectionUtils.isEmpty(at)) {
            return;
        }
        try {
            message.setAt(AtMentionHelper.normalizeAndValidate(at, allGroupMembers));
        } catch (IllegalArgumentException ex) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR, ex.getMessage());
        }
    }
}
