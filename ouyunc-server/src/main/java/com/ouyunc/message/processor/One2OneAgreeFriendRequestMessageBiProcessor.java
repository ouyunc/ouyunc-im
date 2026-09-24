package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.RequestSessionProgress;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSendResultHelper;
import com.ouyunc.message.helper.RequestEventContextFactory;
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
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", "One2OneAgreeFriendRequestMessageBiProcessor.process", packet);
            ctx.close();
            return Mono.just(false);
        }
        // 权限等校验通过后由 continueWhenPassed 归档；此处统一执行 QoS 判重
        if (qosPreHandle(ctx, packet)) {
            return Mono.just(false);
        }
        return MessageAcceptPipelineHelper.continueWhenPassedOrAck(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(FromToValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .verify(packet, ctx),
                "验证不通过。没有权限/被拉黑/发送方和接收方相同，请知悉。该消息 {} 被忽略");
    }


    /**
     * 处理同意好友请求，在发送该消息前，可以判断双方是否已经是好友，如果是好友，则不发送该消息即可，如果选择发送该消息，会给对方推送一条同意的消息，注意逻辑处理；
     * @param ctx
     * @param packet
     */
    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getIngress().getAppKey();
        String sessionId = IdentityUtil.sessionId(from, to);
        return Mono.fromRunnable(() -> DistributedLockHelper.runWithLock(ctx, packet,
                CacheConstant.buildFriendRequestLockCacheKey(appKey, sessionId),
                ExceptionCodeEnum.BIND_FRIEND_ERROR, () -> {
                    RequestSession requestSession = friendAgreeSession(ctx, packet, appKey);
                    if (requestSession == null) {
                        return;
                    }
                    requestSession.setProgress(RequestSessionProgress.AGREEING.value());
                    if (!repository().saveAgreeFriendRequestSession(packet, requestSession,
                            MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    RequestEventContextFactory.capture(packet, requestSession);
                    if (!MessageAcceptPipelineHelper.publishRequestCommand(ctx, MqConstant.MQ_FRIEND_REQUEST_TOPIC, sessionId, packet)) {
                        return;
                    }
                    RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(to));
                    MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
                }));
    }

    /** 通过才允许发布审批命令。已经是好友时直接回受理成功，返回 null 表示不得发布。 */
    private RequestSession friendAgreeSession(ChannelHandlerContext ctx, Packet packet, String appKey) {
        Message message = packet.getMessage();
        RequestSession requestSession = repository().getFriendRequestSession(appKey, message.getTo(), message.getFrom());
        if (requestSession == null) {
            log.warn("不存在加好友请求记录，该消息忽略");
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.REQUEST_SESSION_NOT_EXIST);
            return null;
        }
        if (RequestSessionProgress.REFUSING.value().equals(requestSession.getProgress())) {
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.REQUEST_SESSION_PROGRESS_MISMATCH);
            return null;
        }
        if (repository().isFriend(appKey, message.getFrom(), message.getTo())) {
            MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
            return null;
        }
        return requestSession;
    }
}
