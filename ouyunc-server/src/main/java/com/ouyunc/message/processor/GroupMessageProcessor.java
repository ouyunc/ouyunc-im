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
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


/**
 * 群聊消息处理器
 * 如果在使用过程中存在使用redis 的瓶颈（吞出量） 可以使用 响应式redis 进行改造提高吞吐量 CacheFactory.REACTIVE_REDIS.instance()
 */
public final class GroupMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupMessageProcessor.class);


    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex == null) {
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
                    MessageContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
                    ctx.close();
                    return;
                }
                if (qosPreHandleIfEnabled(ctx, packet)) {
                    return;
                }
                // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）群是否被封禁，是否全体禁言
                PermissionValidator.INSTANCE.negate()
                        .or(FromToValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .or(GroupSilenceValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .verify(packet, ctx)
                        .onErrorResume(error -> {
                            log.error("校验过程中出现异常: {}", error.getMessage());
                            return Mono.just(true); // 出现异常时默认校验不通过
                        }).flatMap(result -> {
                            if (result) {
                                log.warn("权限不足/在黑名单中/不是群成员/被禁言/发送方和接收方相同, 请知悉。该消息 {} 被忽略", packet);
                                return Mono.empty(); // 校验不通过，不传递消息
                            }
                            return Mono.just(packet); // 校验通过，继续传递消息
                        }).subscribe(ctx::fireChannelRead);
            } else {
                // 发送失败
                log.error("Failed to send message: {} ", ex.getMessage());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
            }
        });

    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing group message...");
        // 1. 尝试使用内容处理器
        if (processWithContentProcessor(ctx, packet)) {
            return;
        }
        // 2. 校验群中是否存在群成员
        // 获取群组成员登录标识id，如果群里面没有人是不允许往里面发消息的
        Set<String> groupUserIdentitySet = repository().groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("群组：{}, 不存在群成员！群消息： {}", packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群成员", packet), MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        // 将groupUserIdentitySet排除掉发送方
        if (!groupUserIdentitySet.remove(packet.getMessage().getFrom())) {
            log.error("发送方：{}, 不在群组：{} 中！群消息： {}", packet.getMessage().getFrom(), packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中", packet), MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet, groupUserIdentitySet);
            return;
        }
        Set<String> allGroupMembers = new HashSet<>(groupUserIdentitySet);
        allGroupMembers.add(packet.getMessage().getFrom());
        if (!normalizeGroupAtOrReject(packet, allGroupMembers)) {
            repository().releaseQosClaim(packet);
            return;
        }
        if (!normalizeMessageRefOrReject(packet)) {
            repository().releaseQosClaim(packet);
            return;
        }
        reactiveSaveGroupMessage(packet).subscribe(
                result -> {
                    if (!result) {
                        log.error("群聊会话索引写入失败: {}", packet);
                        publishCachePersistenceError(packet, "群聊消息写入会话失败");
                        repository().releaseQosClaim(packet);
                        return;
                    }
                    // 持久化成功后才发送 QoS ACK
                    sendQosAckAfterPersist(ctx, packet);
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                        repository().saveLastMessageForSession(packet.getMessage().getTo(), packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                        handleWithdrawMessage(ctx, packet, groupUserIdentitySet);
                    } else {
                        repository().saveLastMessageForSession(packet.getMessage().getTo(), packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                        deliverAndFireNext(ctx, packet, groupUserIdentitySet);
                    }
                },
                error -> {
                    log.error("群聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                    publishCachePersistenceError(packet, "群聊持久化异常: " + error.getMessage());
                    repository().releaseQosClaim(packet);
                });

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
    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveValidReadReceiptMessage(packet, packet.getMessage().getTo(), IdentityType.GROUP, true),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, sessionId, packet),
                repository().reactiveReadReceiptMessage(packet, IdentityType.GROUP, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                (ctx0, packet0) -> completeReadReceipt(ctx0, packet0, () -> deliverGroupReadReceiptSelfSyncOnly(packet0)),
                (exceptionEvent)-> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR).subscribe();
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

    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        // 获取当前撤销人员是否是群主或者管理员，他们是最大权限可以撤销所有成员的消息，当然也包括自己
        Set<String> leaderOrManagerIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
        boolean leaderOrManager = CollectionUtils.isNotEmpty(leaderOrManagerIdentitySet) && leaderOrManagerIdentitySet.contains(packet.getMessage().getFrom());
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveValidWithdrawMessage(packet, sessionId, !leaderOrManager),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, sessionId, packet),
                Mono.just(true),
                (ctx0, packet0) -> deliverWithdrawMessageAndFireNext(ctx0, packet0, groupUserIdentitySet),
                (exceptionEvent)-> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .subscribe();
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
        ctx.fireChannelRead(packet);
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
        ctx.fireChannelRead(packet);
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
        AbstractBaseProcessor<? extends Number> processor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
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
        String appKey = packet.getMessage().getMetadata().getAppKey();
        // 一次性批量查询所有群成员的在线状态
        Map<String, List<LoginClientInfo>> onlineMap = ClientHelper.onlineAllBatch(appKey, groupMembers);
        onlineMap.forEach((member, clients) -> {
            if (CollectionUtils.isNotEmpty(clients)) {
                MessageHelper.asyncSendMessage(packet, clients);
            }
        });
    }

    private void deliver2AtMessage(Packet packet, List<String> atList, Set<String> groupMembers) {
        Set<String> targets = AtMentionHelper.resolveDeliveryTargets(atList, groupMembers);
        targets.forEach(member -> deliverMessage(packet, member));
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


    private void deliverMessage(Packet packet, String memberIdentity) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        // 发送给他人
        List<LoginClientInfo> clientInfos = ClientHelper.onlineAll(appKey, memberIdentity);
        if (CollectionUtils.isEmpty(clientInfos)) {
            log.debug("群成员 {} 不在线，已写入群会话索引，上线后拉取", memberIdentity);
            return;
        }
        MessageHelper.asyncSendMessage(packet, clientInfos);
    }
}
