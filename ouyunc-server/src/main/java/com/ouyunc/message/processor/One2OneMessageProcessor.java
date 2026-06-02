package com.ouyunc.message.processor;

import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * 一对一（单聊）消息处理器;
 * 如果在使用过程中存在使用redis 的瓶颈（吞出量） 可以使用 响应式redis 进行改造提高吞吐量 CacheFactory.REACTIVE_REDIS.instance()
 */
public final class One2OneMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        ThreadPoolManager.messageProcessorExecutor().execute(() ->
                repository().save(packet).whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        log.warn("异步归档 packet 到 MQ 失败, packetId={}, 原因: {}",
                                packet.getPacketId(), ex.getMessage(), ex);
                        MessageServerContext.publishEvent(new MessageEvent(
                                ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                                        "异步归档消息到 MQ 失败: " + ex.getMessage(), packet),
                                MessageEventTypeEnum.EXCEPTION), true);
                    }
                }));
        // 两个都校验通过才放行
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return;
        }
        // 校验是否拥有相关权限 permission （是有有单聊，甚至某种内容类型的权限，如不能发语音，视频消息，只能发文本，都可以在这里做校验拦截）
        // 屏蔽和拉黑的效果目前是一样的功能，都不能将将消息发到对方
        // 校验是否被拉黑,如果被拉黑 （无论是否是好友，都可以拉黑）
        // 构建校验逻辑
        PermissionValidator.INSTANCE.negate()
                    .or(FriendValidator.INSTANCE.negate())
                    .or(BlackListValidator.INSTANCE)
                    .or(FriendShieldValidator.INSTANCE)
                    .or(FromToValidator.INSTANCE)
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/不是好友/在黑名单中/被屏蔽/发送方和接收方相同, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    /**
     * 处理一对一消息
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing one-to-one message...");
        // 1. 尝试使用内容处理器
        if (processWithContentProcessor(ctx, packet)) {
            return;
        }
        AtMentionHelper.clearAtIfPresent(packet.getMessage());
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            repository().releaseQosClaim(packet);
            return;
        }
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet);
            return;
        }
        saveMessage(packet).subscribe(
                result -> {
                    if (!result) {
                        log.error("单聊会话索引写入失败: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊消息写入会话失败", packet), MessageEventTypeEnum.EXCEPTION), true);
                        // 持久化失败不发 ACK，等客户端超时重发；同时释放 QoS 占位避免重发被判定为重复
                        repository().releaseQosClaim(packet);
                        return;
                    }
                    // 持久化成功后才发送 QoS ACK，确保「客户端收到 ACK == 服务端持久化成功」
                    if (MessageContext.isQosEnable()) {
                        qosPostHandle(ctx, packet);
                    }
                    repository().saveLastMessageForSession(IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo()), packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    repository().reactiveAdvanceSenderReadOffsetOnSend(
                                    packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(
                                    ignored -> { },
                                    e -> log.warn("发送消息静默更新本端已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                        handleWithdrawMessage(ctx, packet);
                    } else {
                        deliverAndFireNext(ctx, packet, false);
                    }
                },
                error -> {
                    log.error("单聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊持久化异常: " + error.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    // 异常时不发 ACK，并释放 QoS 占位允许客户端重试
                    repository().releaseQosClaim(packet);
                });
    }


    /**
     * 处理消息内容类型是撤回消息
     *
     * @param ctx
     * @param packet
     */
    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String from = packet.getMessage().getFrom();
        String to = packet.getMessage().getTo();
        String sessionId = IdentityUtil.sessionId(from, to);
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(packet, sessionId, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                () -> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, sessionId, packet),
                packets -> repository().reactiveWithdrawMessage(packet, sessionId, packets),
                (ctx0, packet0) -> deliverAndFireNext(ctx0, packet0, true),
                (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .subscribe();
    }

    /**
     * 处理已读回执：不落库为对端「聊天消息」，但需推送给 {@link Message#getTo()}（消息发送方），
     * 以便发送方多端将己方气泡更新为「已读」。阅读方 {@link Message#getFrom()} 不再重复投递。
     * <p>对端亦可根据对方聊天消息 packetId 推断已读，见 {@code docs/read-receipt-session-offset.md}。</p>
     */
    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveValidReadReceiptMessage(packet, sessionId, IdentityType.ONE_2_ONE, false),
                () -> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, sessionId, packet),
                repository().reactiveReadReceiptMessage(packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                (ctx0, packet0) -> {
                    deliverReadReceiptToSender(packet0);
                    ctx0.fireChannelRead(packet0);
                },
                (exceptionEvent)-> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .subscribe();
    }

    /** 将已读回执推送给会话中的消息发送方（packet.message.to），与私聊普通消息投递 to 一致 */
    private void deliverReadReceiptToSender(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }



    /**
     * 发送消息给接收方
     *
     * @param ctx
     * @param packet
     */
    private void deliverAndFireNext(ChannelHandlerContext ctx, Packet packet, Boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        // 同步给自己,可以设置配置开关是否自我同步
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
        if (forceSelfSync || (clientInfo != null && clientInfo.getSelfSync())) {
            List<LoginClientInfo> fromSelfLoginClientInfos = ClientHelper.onlineAll(appKey, message.getFrom(), MessageServerContext.deviceType(appKey, packet.getDeviceType()));
            if (CollectionUtils.isNotEmpty(fromSelfLoginClientInfos)) {
                MessageHelper.asyncSendMessage(packet, fromSelfLoginClientInfos);
            }
        }
        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isEmpty(toLoginClientInfos)) {
            log.debug("接收方 {} 不在线，已写入会话索引，上线后拉取", message.getTo());
        } else {
            MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
        }
        ctx.fireChannelRead(packet);
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
     * 保存消息
     */
    private Mono<Boolean> saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return repository().reactiveSaveOne2OneMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }



}
