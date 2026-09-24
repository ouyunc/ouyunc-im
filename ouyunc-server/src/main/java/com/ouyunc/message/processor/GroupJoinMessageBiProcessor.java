package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.GroupBindResultHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSendResultHelper;
import com.ouyunc.message.helper.RequestEventContextFactory;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 主动加群：群开启自动同意则直接绑定并通知申请人；需审核时仅通知群主/管理员（发起方不推送）。
 */
public final class GroupJoinMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupJoinMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_JOIN;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupJoinMessageBiProcessor.process", packet);
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
                        .or(GroupMaxLimitValidator.INSTANCE)
                        .or(GroupUserMaxLimitValidator.INSTANCE)
                        .verify(packet, ctx),
                "权限不足/在黑名单中/群异常（被平台封禁）/群或成员数超限/接收者和发送者相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        return MessageAcceptPipelineHelper.confirmThenRun(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet, () -> {
            String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, message.getFrom(), message.getTo());
            DistributedLockHelper.runWithLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
                GroupRequestSession existingSession = repository().getGroupRequestSession(appKey, message.getFrom(), message.getTo());
                if (repository().inGroup(appKey, message.getFrom(), message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}，幂等 ACK", message.getFrom(), message.getTo());
                    if (!repository().repairUserGroupIndex(appKey, message.getFrom(), message.getTo(),
                            message.getMetadata().getIngress().getServerTime())) {
                        MessageSendResultHelper.retryLater(ctx, packet, ExceptionCodeEnum.BIND_GROUP_ERROR);
                        return;
                    }
                    // 上次可能在群关系写入后、请求会话提交前退出；重试必须补齐请求状态。
                    if (existingSession == null) {
                        existingSession = newActiveGroupSession(message, RequestSessionProgress.AGREEING.value());
                    } else {
                        existingSession.setProgress(RequestSessionProgress.AGREEING.value());
                        existingSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                        existingSession.setWay(GroupRequestSessionWay.ACTIVE.value());
                    }
                    if (!repository().saveGroupRequestMessage(packet, existingSession,
                            MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    if (!publishGroupCommand(ctx, packet, existingSession)) {
                        return;
                    }
                    RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(message.getFrom()));
                    MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
                    return;
                }
                if (null != existingSession && (existingSession.getProgress() > RequestSessionProgress.JOINING.value()
                        || !GroupRequestSessionWay.ACTIVE.value().equals(existingSession.getWay()))) {
                    log.warn("{} 和 {} 群请求会话残留 progress={} way={}，清除后允许重新申请",
                            message.getFrom(), message.getTo(), existingSession.getProgress(), existingSession.getWay());
                    repository().deleteGroupRequestSession(appKey, message.getFrom(), message.getTo());
                    existingSession = null;
                }
                GroupEntity groupEntity = repository().getGroupEntity(appKey, message.getTo());
                if (groupEntity == null) {
                    log.error("群组:{} 不存在，请检查数据！", message.getTo());
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_NOT_EXIST, message.getTo() + "群组不存在！", "GroupJoinMessageBiProcessor.process", packet);
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
                if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", "GroupJoinMessageBiProcessor.process", packet);
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Set<String> notifyManagerAndLeaderUserIds = new HashSet<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                Set<String> groupMannerOrLeaderUsersIdentitySet = new HashSet<>(notifyManagerAndLeaderUserIds);
                if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom()) || CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
                    log.error("群组：{}, 不存在群主和群管理员或群消息或已经加入群组： {}", packet.getMessage().getTo(), packet);
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", "GroupJoinMessageBiProcessor.process", packet);
                    MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                GroupRequestSession groupRequestSession = existingSession != null ? existingSession
                        : newActiveGroupSession(message, RequestSessionProgress.JOINING.value());
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                groupRequestSession.setWay(GroupRequestSessionWay.ACTIVE.value());

                Set<String> notifyIdentities;
                if (GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy())) {
                    groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                    if (!GroupBindResultHelper.acceptedOrReply(ctx, packet,
                            repository().autoPassBindGroup(packet, groupRequestSession,
                                    MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP,
                                    GroupBindResultHelper.maxMembers(), GroupBindResultHelper.maxPerUser()),
                            "自动绑定群组请求消息异常!")) {
                        return;
                    }
                    notifyIdentities = RequestNotifyHelper.userOnly(message.getFrom());
                } else {
                    groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession, existingSession != null)) {
                        log.error("Failed to save join group request message: {}", packet);
                        ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", "GroupJoinMessageBiProcessor.process", packet);
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    notifyIdentities = RequestNotifyHelper.copyOf(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                }
                if (!publishGroupCommand(ctx, packet, groupRequestSession)) {
                    return;
                }
                RequestNotifyHelper.dispatch(ctx, packet, appKey, notifyIdentities);
                MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
            });
        });
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession, boolean upsert) {
        if (upsert) {
            return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
        }
        return repository().saveJoinGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

    private static GroupRequestSession newActiveGroupSession(Message message, int progress) {
        return GroupRequestSession.newGroupBuilder()
                .sessionId(message.getId())
                .progress(progress)
                .joiner(message.getFrom())
                .groupId(message.getTo())
                .channel(GroupRequestSessionChannel.OTHER.value())
                .way(GroupRequestSessionWay.ACTIVE.value())
                .joinerProcessStatus(GroupJoinerProcessStatus.AGREE.value())
                .build();
    }

    private static boolean publishGroupCommand(ChannelHandlerContext ctx, Packet packet, GroupRequestSession session) {
        RequestEventContextFactory.capture(packet, session);
        return MessageAcceptPipelineHelper.publishRequestCommand(
                ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet);
    }

}
