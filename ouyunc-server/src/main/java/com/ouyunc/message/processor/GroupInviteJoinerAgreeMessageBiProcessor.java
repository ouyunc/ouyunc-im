package com.ouyunc.message.processor;

import com.ouyunc.core.context.MessageContext;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.GroupBindResultHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSubmissionResponseHelper;
import com.ouyunc.message.helper.RequestEventContextFactoryHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 被邀请人同意加群：免审入群仅通知被邀请人；仍需管理员审时仅通知群主/管理员（被邀请人/邀请人均不推送）。
 */
public final class GroupInviteJoinerAgreeMessageBiProcessor extends AbstractRequestMessageBiProcessor {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinerAgreeMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_AGREE;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupInviteJoinerAgreeMessageBiProcessor.process", packet);
            ctx.close();
            return Mono.just(false);
        }
        // 权限等校验通过后由 continueWhenPassed 进入 process；此处统一执行 QoS 判重
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
                "权限不足/在黑名单中/群异常（被平台封禁）/已经是群成员/群或成员数超限/接受者和发送者相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinerAgreeMessageProcessor 正在处理被邀请加群者同意加群的请求 {} ...", packet);
        }
        Message message = packet.getMessage();
        String joiner = message.getFrom();
        String appKey = message.getMetadata().getIngress().getAppKey();
        return MessageAcceptPipelineHelper.confirmThenRun(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet, () -> {
            String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, joiner, message.getTo());
            DistributedLockHelper.runWithLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
                // 同方向重复操作可继续补保存/发布；相反决定或管理员已开始相反审批时仍拒绝。
                GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, joiner, message.getTo());
                if (null == groupRequestSession || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()) || StringUtils.isBlank(groupRequestSession.getInviter()) || RequestSessionProgress.REFUSING.value().equals(groupRequestSession.getProgress())
                        || (!Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.PENDING.value())
                        && !Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.AGREE.value()))) {
                    log.warn("{} 和 {} 不存在正在处理中的群会话请求或当前群请求不是邀请或邀请人为空或存在拒绝或同意还未结束处理", joiner, message.getTo());
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
                if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", "GroupInviteJoinerAgreeMessageBiProcessor.process", packet);
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Set<String> groupMannerOrLeaderUsersIdentitySet = new HashSet<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom());
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                GroupEntity groupEntity = repository().getGroupEntity(appKey, message.getTo());
                // 历史岗位只供审计；撤权后尚未接受的邀请不得继续享有免审权限。
                Double currentInviterPost = groupMannerOrLeaderUsersIdentityAndPostMap.get(groupRequestSession.getInviter());
                boolean inviterIsManagerOrLeader = currentInviterPost != null;
                if (inviterIsManagerOrLeader) {
                    groupRequestSession.setInviterPost(currentInviterPost.intValue());
                }
                boolean canSkipAdminReview = repository().inGroup(appKey, joiner, message.getTo())
                        || inviterIsManagerOrLeader
                        || (groupEntity != null && GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy()));
                if (canSkipAdminReview) {
                    groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                    if (inviterIsManagerOrLeader) {
                        groupRequestSession.setProcessor(groupRequestSession.getInviter());
                        if (groupRequestSession.getInviterPost() != null) {
                            groupRequestSession.setProcessorPost(groupRequestSession.getInviterPost());
                        }
                    }
                    if (!GroupBindResultHelper.acceptedOrReply(ctx, packet,
                            repository().autoPassBindGroup(packet, groupRequestSession,
                                    MessageContext.messageHotDataTtlMillis(),
                                    GroupBindResultHelper.maxMembers(), GroupBindResultHelper.maxPerUser()),
                            "自动绑定群组请求消息异常!")) {
                        return;
                    }
                    if (!publishGroupCommand(ctx, packet, groupRequestSession)) {
                        return;
                    }

                } else {
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession)) {
                        log.error("Failed to save invited join group agree request message: {}", packet);
                        ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存被邀请同意加群请求消息异常!", "GroupInviteJoinerAgreeMessageBiProcessor.process", packet);
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    if (!publishGroupCommand(ctx, packet, groupRequestSession)) {
                        return;
                    }

                }
                MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
            });
        });
    }


    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageContext.messageHotDataTtlMillis());
    }

    private static boolean publishGroupCommand(ChannelHandlerContext ctx, Packet packet, GroupRequestSession session) {
        RequestEventContextFactoryHelper.capture(packet, session);
        return MessageAcceptPipelineHelper.publishRequestCommand(
                ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet);
    }


}
