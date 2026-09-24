package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.GroupBindResultHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSendResultHelper;
import com.ouyunc.message.helper.RequestEventContextFactory;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 处理人同意 加/邀请 群
 */
public final class GroupAgreeMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupAgreeMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_AGREE;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupAgreeMessageBiProcessor.process", packet);
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
                        .or(GroupValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .or(GroupUserMaxLimitValidator.INSTANCE)
                        .verify(packet, ctx),
                "权限不足/在黑名单中/群异常（被平台封禁)/不是群成员/群成员数超限/接受者和发送者相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupAgreeMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        if (MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType() != message.getContentType()) {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return Mono.empty();
        }
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        } else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.MESSAGE_CONTENT_TYPE_ERROR, "消息内容类型错误", "GroupAgreeMessageBiProcessor.process", packet);
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return Mono.empty();
        }
        String appKey = message.getMetadata().getAppKey();
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, content.getIdentity(), message.getTo());
        return Mono.fromRunnable(() -> DistributedLockHelper.runWithLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            java.util.Set<String> notifyIds = approveTargets(ctx, packet, content, appKey);
            if (notifyIds == null) {
                return;
            }
            GroupRequestSession session = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
            if (session == null) {
                MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                return;
            }
            session.setProgress(RequestSessionProgress.AGREEING.value());
            if (!repository().saveGroupRequestMessage(packet, session, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                return;
            }
            RequestEventContextFactory.capture(packet, session);
            if (!MessageAcceptPipelineHelper.publishRequestCommand(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet)) {
                return;
            }
            RequestNotifyHelper.dispatch(ctx, packet, appKey, notifyIds);
            MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
        }));
    }

    /** 校验通过返回通知目标；失败时已回拒绝结果，返回 null，调用方不得发布命令。 */
    private java.util.Set<String> approveTargets(ChannelHandlerContext ctx, Packet packet, GroupRequestContent content, String appKey) {
        Message message = packet.getMessage();
        GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
        if (groupRequestSession == null) {
            log.warn("{} 和 {} 会话请求不存在", content.getIdentity(), message.getTo());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.REQUEST_SESSION_NOT_EXIST);
            return null;
        }
        if (RequestSessionProgress.REFUSING.value().equals(groupRequestSession.getProgress())) {
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.REQUEST_SESSION_PROGRESS_MISMATCH);
            return null;
        }
        GroupRequestSessionWay way = GroupRequestSessionWay.valueOf(groupRequestSession.getWay());
        if (way == null) {
            log.error("非法群会话请求方式：{}", groupRequestSession.getWay());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return null;
        }
        if (GroupRequestSessionWay.INVITED.equals(way) && message.getFrom().equals(content.getIdentity())) {
            log.warn("发送方: {} 和加入方: {} 相同，忽略 该请求", message.getFrom(), content.getIdentity());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return null;
        }
        if (GroupRequestSessionWay.INVITED.equals(way) && !GroupJoinerProcessStatus.AGREE.value().equals(groupRequestSession.getJoinerProcessStatus())) {
            log.error("被邀请人 {} 尚未同意邀请，请等待", groupRequestSession.getJoiner());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return null;
        }
        if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
            MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
            return null;
        }
        Map<String, Double> managers = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
        if (MapUtils.isEmpty(managers)) {
            log.error("群组：{}, 不存在群主！群消息： {}", packet.getMessage().getTo(), packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主和群管理员", "GroupAgreeMessageBiProcessor.process", packet);
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return null;
        }
        if (!managers.containsKey(message.getFrom())) {
            log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return null;
        }
        managers.remove(message.getFrom());
        return RequestNotifyHelper.withUser(RequestNotifyHelper.copyExcept(managers.keySet(), message.getFrom()), content.getIdentity());
    }


}
