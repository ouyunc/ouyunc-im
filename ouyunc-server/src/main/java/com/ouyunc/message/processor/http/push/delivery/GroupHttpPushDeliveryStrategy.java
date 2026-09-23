package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.GroupMessagePushModeEnum;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushFailures;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import com.ouyunc.message.processor.http.push.IngressPacketHelper;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.support.GroupMembershipSupport;
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
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String groupId = message.getTo();
        boolean skipSenderMembership = IngressPacketHelper.isHttpPush(packet)
                && IngressPacketHelper.isSystemLikeSender(message);
        if (!skipSenderMembership && !DefaultRepository.INSTANCE.inGroup(appKey, message.getFrom(), groupId)) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中");
        }
        requireValidGroupAt(packet, appKey, groupId);
    }

    @Override
    public Mono<Boolean> processMono(Packet packet) {
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return handleWithdraw(packet);
        }
        return saveAndDeliverChat(packet);
    }

    private Mono<Boolean> saveAndDeliverChat(Packet packet) {
        return DefaultRepository.INSTANCE.reactiveSaveMessage(packet, packet.getMessage().getTo(),
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(outcome -> {
                    if (outcome != null && outcome.isDuplicate()) {
                        return replayOnline(packet);
                    }
                    if (outcome == null || !outcome.isFreshWrite()) {
                        log.error("HTTP 推送群聊落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "群聊消息写入会话失败", packet);
                        return Mono.just(false);
                    }
                    try {
                        DefaultRepository.INSTANCE.saveLastMessageForSession(packet.getMessage().getTo(), packet,
                                MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.warn("HTTP 推送更新群聊最后消息失败，继续投递 packetId={}", packet.getPacketId(), e);
                    }
                    DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.GROUP,
                                    MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(ignored -> { }, e -> log.warn(
                                    "HTTP 推送更新群聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    pushGroupOnline(packet);
                    return Mono.just(true);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送群聊落库异常, packetId={}", packet.getPacketId(), error);
                    ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "群聊持久化异常: " + error.getMessage(),
                            "GroupHttpPushDeliveryStrategy.process", packet, error);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> handleWithdraw(Packet packet) {
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
                            deliverWithdraw(packet0);
                        },
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
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    private static void deliverGroupReadReceiptSelfSyncOnly(Packet packet) {
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, packet.getMessage().getFrom());
    }

    @Override
    public Mono<Boolean> replayOnline(Packet packet) {
        return Mono.fromCallable(() -> {
            int contentType = packet.getMessage().getContentType();
            if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
                deliverGroupReadReceiptSelfSyncOnly(packet);
            } else if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                deliverWithdraw(packet);
            } else {
                pushGroupOnline(packet);
            }
            return Boolean.TRUE;
        });
    }

    private static void deliverWithdraw(Packet packet) {
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, packet.getMessage().getFrom());
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, loadFullMembersOrEmpty(packet));
    }

    private static void pushGroupOnline(Packet packet) {
        Message message = packet.getMessage();
        HttpPushDeliverySupport.syncSenderOnlineDevices(packet, message.getFrom());
        GroupMessagePushModeEnum mode = MessageServerContext.serverProperties().getGroupMessagePushMode();
        if (GroupMessagePushModeEnum.PUSH.equals(mode)) {
            deliverToAllGroupMembers(packet, loadFullMembersOrEmpty(packet));
            return;
        }
        if (GroupMessagePushModeEnum.PULL.equals(mode)) {
            deliverAtMentionsIfAny(packet);
            return;
        }
        if (GroupMessagePushModeEnum.PULL_PUSH.equals(mode)) {
            long memberCount = DefaultRepository.INSTANCE.groupMemberCount(
                    message.getMetadata().getAppKey(), message.getTo());
            if (memberCount > MessageServerContext.serverProperties().getGroupMessageThreshold()) {
                deliverAtMentionsIfAny(packet);
            } else {
                deliverToAllGroupMembers(packet, loadFullMembersOrEmpty(packet));
            }
            return;
        }
        log.warn("HTTP 推送暂不支持群消息推送模式: {}", mode);
    }

    private static void deliverToAllGroupMembers(Packet packet, Set<String> groupMembers) {
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, groupMembers);
    }

    private static void deliverAtMentionsIfAny(Packet packet) {
        List<String> atList = packet.getMessage().getAt();
        if (CollectionUtils.isEmpty(atList)) {
            return;
        }
        if (AtMentionHelper.containsAtAll(atList)) {
            deliverToAllGroupMembers(packet, loadFullMembersOrEmpty(packet));
            return;
        }
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, new HashSet<>(atList));
    }

    private static Set<String> loadFullMembersOrEmpty(Packet packet) {
        try {
            Set<String> members = DefaultRepository.INSTANCE.groupUsersIdentity(packet);
            return members == null ? Set.of() : members;
        } catch (GroupMembershipSupport.GroupMembershipLoadException e) {
            log.error("HTTP 枚举群成员失败 group={} packetId={}",
                    packet.getMessage().getTo(), packet.getPacketId(), e);
            return Set.of();
        }
    }

    private static void requireValidGroupAt(Packet packet, String appKey, String groupId)
            throws HttpPipelineException {
        Message message = packet.getMessage();
        List<String> at = message.getAt();
        if (CollectionUtils.isEmpty(at)) {
            return;
        }
        try {
            List<String> explicit = AtMentionHelper.explicitMemberIds(at);
            Set<String> confirmed = DefaultRepository.INSTANCE.presentInGroup(appKey, groupId, explicit);
            message.setAt(AtMentionHelper.normalizeAndValidate(at, confirmed));
        } catch (GroupMembershipSupport.GroupMembershipLoadException | IllegalArgumentException ex) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR, ex.getMessage());
        }
    }
}
