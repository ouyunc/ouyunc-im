package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.base.constant.enums.RequestSessionProgress;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.FromToValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * 同意加好友：仅通知申请人（审批操作者不推送）。
 */
public final class One2OneAgreeFriendRequestMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneAgreeFriendRequestMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_AGREE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }



    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        preProcessStage(ctx, packet).subscribe();
    }

    @Override
    public Mono<Void> preProcessStage(ChannelHandlerContext ctx, Packet packet) {
        repository().save(packet);
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
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
                        .verify(packet, ctx),
                null,
                "验证不通过。没有权限/被拉黑/发送方和接收方相同，请知悉。该消息 {} 被忽略");
    }


    /**
     * 处理同意好友请求，在发送该消息前，可以判断双方是否已经是好友，如果是好友，则不发送该消息即可，如果选择发送该消息，会给对方推送一条同意的消息，注意逻辑处理；
     * @param ctx
     * @param packet
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String sessionId = IdentityUtil.sessionId(from, to);
        String lockKey = CacheConstant.buildFriendRequestLockCacheKey(appKey, sessionId);

        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_FRIEND_ERROR, () -> {
            RequestSession requestSession = repository().getFriendRequestSession(appKey, message.getTo(), message.getFrom());
            if (null == requestSession || !Objects.equals(requestSession.getProgress(), RequestSessionProgress.JOINING.value())) {
                log.warn("不存在加好友请求记录或存在正在处理的好友请求，该消息忽略");
                return;
            }
            if (repository().isFriend(appKey, message.getFrom(), message.getTo())) {
                log.warn("已经是好友, 请知悉; {}", packet);
                return;
            }
            requestSession.setProgress(RequestSessionProgress.AGREEING.value());
            if (!repository().agreeBindFriend(appKey, packet, requestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                log.error("绑定好友关系异常: {}", packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.BIND_FRIEND_ERROR, "处理一对一同意好友请求绑定异常！", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(to));
            repository().publishPacketAsync(MqConstant.MQ_FRIEND_REQUEST_TOPIC, sessionId, packet,
                    "处理一对一同意好友请求 MQ 旁路");
        });
    }
}
