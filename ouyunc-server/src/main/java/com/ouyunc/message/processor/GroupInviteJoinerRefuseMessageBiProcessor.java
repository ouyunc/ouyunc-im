package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSendResultHelper;
import com.ouyunc.message.helper.RequestEventContextFactory;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 被邀请人拒绝邀请：仅通知群主/管理员归档；不通知邀请人及被邀请人本人。
 */
public final class GroupInviteJoinerRefuseMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinerRefuseMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_REFUSE;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupInviteJoinerRefuseMessageBiProcessor.process", packet);
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
                        .or(GroupUserValidator.INSTANCE)
                        .verify(packet, ctx),
                "权限不足/在黑名单中/群异常（被平台封禁）/已经是群成员/接受者和发送者相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinerRefuseMessageProcessor 正在处理被邀请加群者同意加群的请求 {} ...", packet);
        }
        Message message = packet.getMessage();
        String joiner = message.getFrom();
        String appKey = message.getMetadata().getIngress().getAppKey();
        return MessageAcceptPipelineHelper.confirmThenRun(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet, () -> {
            String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, joiner, message.getTo());
            DistributedLockHelper.runWithLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
                GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, joiner, message.getTo());
                if (null == groupRequestSession || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()) || StringUtils.isBlank(groupRequestSession.getInviter()) || !Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.PENDING.value())) {
                    log.warn("{} 和 {} 不存在正在处理中的群会话请求或当前群请求不是邀请或邀请人为空或存在拒绝或同意还未结束处理", joiner, message.getTo());
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                if (repository().inGroup(appKey, joiner, message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}", joiner, message.getTo());
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
                if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", "GroupInviteJoinerRefuseMessageBiProcessor.process", packet);
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Set<String> groupMannerOrLeaderUsersIdentitySet = new HashSet<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom())) {
                    log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.REFUSE.value());
                if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession)) {
                    log.error("Failed to save invited join group refuse request message: {}", packet);
                    ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存被邀请拒绝加群请求消息异常!", "GroupInviteJoinerRefuseMessageBiProcessor.process", packet);
                    MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                    return;
                }
                if (!publishGroupCommand(ctx, packet, groupRequestSession)) {
                    return;
                }
                RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.copyOf(groupMannerOrLeaderUsersIdentityAndPostMap.keySet()));
                MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
            });
        });
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

    private static boolean publishGroupCommand(ChannelHandlerContext ctx, Packet packet, GroupRequestSession session) {
        RequestEventContextFactory.capture(packet, session);
        return MessageAcceptPipelineHelper.publishRequestCommand(
                ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet);
    }

}
