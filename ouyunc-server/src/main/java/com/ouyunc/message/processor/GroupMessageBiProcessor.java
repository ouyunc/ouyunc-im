package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.processor.http.push.IngressPacketHelper;
import com.ouyunc.message.validator.*;
import com.ouyunc.repository.support.MessageIndexScope;
import io.netty.channel.ChannelHandlerContext;
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
 * 群聊消息处理器。
 * <p>普通群消息持久化成功后回 QoS ACK；已读回执、撤回在对应操作成功后再 ACK。</p>
 */
public final class GroupMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupMessageBiProcessor.class);


    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        preProcessStage(ctx, packet).subscribe();
    }

    @Override
    public Mono<Void> preProcessStage(ChannelHandlerContext ctx, Packet packet) {
        repository().save(packet);
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            MessageContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return Mono.empty();
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return Mono.empty();
        }
        return fireWhenPassed(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(FromToValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .or(GroupSilenceValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .verify(packet, ctx),
                () -> releaseQosOnFailure(packet),
                "权限不足/在黑名单中/不是群成员/被禁言/发送方和接收方相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        processStage(ctx, packet).subscribe();
    }

    @Override
    public Mono<Void> processStage(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing group message...");
        if (processWithContentProcessor(ctx, packet)) {
            return Mono.empty();
        }
        Set<String> groupUserIdentitySet = repository().groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("群组：{}, 不存在群成员！群消息： {}", packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群成员", packet), MessageEventTypeEnum.EXCEPTION), true);
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        boolean skipSenderMembership = IngressPacketHelper.isHttpPush(packet)
                && IngressPacketHelper.isSystemLikeSender(packet.getMessage());
        if (!skipSenderMembership && !groupUserIdentitySet.remove(packet.getMessage().getFrom())) {
            log.error("发送方：{}, 不在群组：{} 中！群消息： {}", packet.getMessage().getFrom(), packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中", packet), MessageEventTypeEnum.EXCEPTION), true);
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        Set<String> allGroupMembers = new HashSet<>(groupUserIdentitySet);
        allGroupMembers.add(packet.getMessage().getFrom());
        if (!normalizeGroupAtOrReject(packet, allGroupMembers)) {
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(ctx, packet, groupUserIdentitySet);
        }
        return reactiveSaveGroupMessage(packet)
                .flatMap(result -> afterGroupSaved(ctx, packet, groupUserIdentitySet, contentType, result))
                .onErrorResume(error -> {
                    log.error("群聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "群聊持久化异常: " + error.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    releaseQosOnFailure(packet);
                    return Mono.empty();
                });
    }

    private Mono<Void> afterGroupSaved(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet,
                                       int contentType, Boolean result) {
        if (!Boolean.TRUE.equals(result)) {
            log.error("群聊会话索引写入失败: {}", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "群聊消息写入会话失败", packet), MessageEventTypeEnum.EXCEPTION), true);
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        if (MessageContext.isQosEnable()
                && MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
            qosPostHandle(ctx, packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
            repository().saveLastMessageForSession(packet.getMessage().getTo(), packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
        repository().reactiveAdvanceSenderReadOffsetOnSend(
                        packet, IdentityType.GROUP, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        ignored -> { },
                        e -> log.warn("发送消息静默更新本端已读 offset 失败, packetId={}", packet.getPacketId(), e));
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return handleWithdrawMessage(ctx, packet, groupUserIdentitySet);
        }
        deliverAndFireNext(ctx, packet, groupUserIdentitySet);
        return Mono.empty();
    }

    private void qosAckOnSuccess(ChannelHandlerContext ctx, Packet packet) {
        if (MessageContext.isQosEnable()) {
            qosPostHandle(ctx, packet);
        }
    }

    private void releaseQosOnFailure(Packet packet) {
        if (MessageContext.isQosEnable()) {
            repository().releaseQosClaim(packet);
        }
    }


    /**
     * 处理消息内容类型是已读消息
     * @param ctx
     * @param packet
     */

    /**
     * 群已读回执：仅校验并写入 Redis/MQ（{@code SessionMessageOffset}），不向群成员广播，避免每人读一次产生 (N-1) 次推送风暴。
     * 发送方「已读」展示应走 HTTP 拉取各成员 offset 或产品层不做群聊逐条已读（见业务文档）。
     * 阅读方多端同步仍可通过 selfSync 投递给自己其它终端。
     */
    private Mono<Void> handleReadReceipt(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadValidatedReadReceiptPackets(
                        packet, packet.getMessage().getTo(), IdentityType.GROUP, false),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveReadReceiptMessage(
                        packet, IdentityType.GROUP,
                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                (ctx0, packet0) -> {
                    qosAckOnSuccess(ctx0, packet0);
                    deliverGroupReadReceiptSelfSyncOnly(packet0);
                    PacketChannelWriter.fireChannelRead(ctx0, packet0);
                },
                (exceptionEvent)-> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
                    }
                })
                .then();
    }

    /** 群已读不回推全员，仅 selfSync 时同步阅读方其它设备 */
    private void deliverGroupReadReceiptSelfSyncOnly(Packet packet) {
        Message message = packet.getMessage();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(message.getMetadata().getAppKey(), message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            deliver2SelfAndFireNext(packet);
        }
    }


    /**
     * 处理消息内容类型是撤回消息
     * @param ctx
     * @param packet
     */

    private Mono<Void> handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        // 获取当前撤销人员是否是群主或者管理员，他们是最大权限可以撤销所有成员的消息，当然也包括自己
        Set<String> leaderOrManagerIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
        boolean leaderOrManager = CollectionUtils.isNotEmpty(leaderOrManagerIdentitySet) && leaderOrManagerIdentitySet.contains(packet.getMessage().getFrom());
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, !leaderOrManager),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveWithdrawMessage(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, packets),
                (ctx0, packet0) -> {
                    qosAckOnSuccess(ctx0, packet0);
                    Message msg = packet0.getMessage();
                    if (msg != null && msg.getMetadata() != null) {
                        String appKey = msg.getMetadata().getAppKey();
                        if (StringUtils.isNoneBlank(appKey, sessionId)) {
                            repository().refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                        }
                    }
                    deliverWithdrawMessageAndFireNext(ctx0, packet0, groupUserIdentitySet);
                },
                (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
                    }
                })
                .then();
    }



    /**
     * 发送消息给接收方
     *
     * @param ctx
     * @param packet
     */
    private void deliverWithdrawMessageAndFireNext(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        Message message = packet.getMessage();
        // 同步发送给自己
        ClientInfo clientInfo = MessageServerContext.localClientInfo(message.getMetadata().getAppKey(), message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            deliver2SelfAndFireNext(packet);
        }
        // 发送给他人
        deliver2AllGroupMembers(packet, groupUserIdentitySet);
        // 处理成功则转到下个处理器
        PacketChannelWriter.fireChannelRead(ctx, packet);
    }


    /**
     * 发送消息给接收方
     *
     * @param ctx
     * @param packet
     */
    private void deliverAndFireNext(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        Message message = packet.getMessage();
        // 同步发送给自己
        ClientInfo clientInfo = MessageServerContext.localClientInfo(message.getMetadata().getAppKey(), message.getFrom());
        if (clientInfo != null && clientInfo.getSelfSync()) {
            deliver2SelfAndFireNext(packet);
        }
        // 判断群消息的推送模式 推送还是拉取还是混合模式
        if (GroupMessagePushModeEnum.PUSH.equals(MessageServerContext.serverProperties().getGroupMessagePushMode())) {
            deliver2AllGroupMembers(packet, groupUserIdentitySet);
        }else if (GroupMessagePushModeEnum.PULL.equals(MessageServerContext.serverProperties().getGroupMessagePushMode())) {
            // 发送给@ 的人
            List<String> atList = message.getAt();
            if (CollectionUtils.isNotEmpty(atList)) {
                deliver2AtMessage(packet, atList, groupUserIdentitySet);
            }
        }else if (GroupMessagePushModeEnum.PULL_PUSH.equals(MessageServerContext.serverProperties().getGroupMessagePushMode())) {
            // 混合模式(推拉模式)
            if (groupUserIdentitySet.size() + NumberConstant.NUMBER_1 > MessageServerContext.serverProperties().getGroupMessageThreshold()) {
                // 发送给@ 的人
                List<String> atList = message.getAt();
                if (CollectionUtils.isNotEmpty(atList)) {
                    deliver2AtMessage(packet, atList, groupUserIdentitySet);
                }
            }else {
                // 发送全体成员
                deliver2AllGroupMembers(packet, groupUserIdentitySet);
            }
        }else {
            log.warn("暂不支持该消息推送模式:{}, 消息：{}", MessageServerContext.serverProperties().getGroupMessagePushMode(), packet);
        }
        // 处理成功则转到下个处理器
        PacketChannelWriter.fireChannelRead(ctx, packet);
    }

    /**
     * 发送消息给自己的其他客戶端
     *
     * @param packet
     */
    private void deliver2SelfAndFireNext(Packet packet) {
        // 同步发送给自己
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        // 同步发送给自己
        List<LoginClientInfo> fromSelfLoginClientInfos = ClientHelper.onlineAll(appKey, message.getFrom(), MessageServerContext.deviceType(appKey, packet.getDeviceType()));
        if (CollectionUtils.isNotEmpty(fromSelfLoginClientInfos)) {
            MessageHelper.asyncSendMessage(packet, fromSelfLoginClientInfos);
        }
    }

    /**
     * 使用内容处理器处理消息
     */
    private boolean processWithContentProcessor(ChannelHandlerContext ctx, Packet packet) {
        AbstractBaseBiProcessor<? extends Number> processor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (processor != null) {
            processor.process(ctx, packet);
            return true;
        }
        return false;
    }


    /**
     * 保存群组消息
     */
    private Mono<Boolean> reactiveSaveGroupMessage(Packet packet) {
        Message message = packet.getMessage();
        return repository().reactiveSaveMessage(packet, message.getTo(), MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }



    private void deliver2AllGroupMembers(Packet packet, Set<String> groupMembers) {
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, groupMembers);
    }

    private void deliver2AtMessage(Packet packet, List<String> atList, Set<String> groupMembers) {
        Set<String> targets = AtMentionHelper.resolveDeliveryTargets(atList, groupMembers);
        MessageDeliveryRouteHelper.deliverGroupMembers(packet, targets);
    }

    /**
     * 校验并规范化群 @ 列表；失败时发布异常事件并返回 false。
     */
    private boolean normalizeGroupAtOrReject(Packet packet, Set<String> allGroupMembers) {
        Message message = packet.getMessage();
        List<String> at = message.getAt();
        if (CollectionUtils.isEmpty(at)) {
            return true;
        }
        try {
            message.setAt(AtMentionHelper.normalizeAndValidate(at, allGroupMembers));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("群@校验失败: {} | packet={}", ex.getMessage(), packet);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_AT_MENTION_INVALID_ERROR, ex.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            return false;
        }
    }
}
