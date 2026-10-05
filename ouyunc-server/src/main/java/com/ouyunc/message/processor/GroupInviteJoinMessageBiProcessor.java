package com.ouyunc.message.processor;

import com.ouyunc.core.context.MessageContext;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.GroupBindResultHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSubmissionResponseHelper;
import com.ouyunc.message.helper.RequestEventContextFactoryHelper;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 邀请加群：发起方（邀请人）不推送；待被邀请人确认时仅通知被邀请人；待管理员审时仅通知群主/管理员；免审入群仅通知被邀请人。
 */
public final class GroupInviteJoinMessageBiProcessor extends AbstractRequestMessageBiProcessor {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", "GroupInviteJoinMessageBiProcessor.process", packet);
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
                        .or(GroupInviteSelfValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .or(GroupValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .or(GroupUserMaxLimitValidator.INSTANCE)
                        .verify(packet, ctx),
                "权限不足/在黑名单中/群异常（被平台封禁）/不是群成员/不能邀请自己/群成员数超限/接受者和发送者相同, 请知悉。该消息 {} 被忽略");
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        } else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
            return Mono.empty();
        }
        return MessageAcceptPipelineHelper.confirmThenRun(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet, () -> {
            String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, content.getIdentity(), message.getTo());
            DistributedLockHelper.runWithLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
                GroupRequestSession existingSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
                if (message.getFrom().equals(content.getIdentity())) {
                    log.warn("发送方: {} 和加入方: {} 相同，忽略 该请求", message.getFrom(), content.getIdentity());
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                // 被邀请人已在群：对齐主动加群——repair 索引、热会话 AGREEING、publish，再 ACK（勿 reject）
                if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}，幂等 ACK", content.getIdentity(), message.getTo());
                    if (!repository().repairUserGroupIndex(appKey, content.getIdentity(), message.getTo(),
                            message.getMetadata().getIngress().getServerTime())) {
                        MessageSubmissionResponseHelper.retryLater(ctx, packet, ExceptionCodeEnum.BIND_GROUP_ERROR);
                        return;
                    }
                    if (existingSession == null) {
                        existingSession = GroupRequestSession.newGroupBuilder()
                                .sessionId(message.getId())
                                .progress(RequestSessionProgress.AGREEING.value())
                                .joiner(content.getIdentity())
                                .groupId(message.getTo())
                                .inviter(message.getFrom())
                                .channel(GroupRequestSessionChannel.OTHER.value())
                                .way(GroupRequestSessionWay.INVITED.value())
                                .joinerProcessStatus(GroupJoinerProcessStatus.AGREE.value())
                                .build();
                    } else {
                        existingSession.setProgress(RequestSessionProgress.AGREEING.value());
                        existingSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                        existingSession.setWay(GroupRequestSessionWay.INVITED.value());
                        if (StringUtils.isBlank(existingSession.getInviter())) {
                            existingSession.setInviter(message.getFrom());
                        }
                    }
                    if (!repository().saveGroupRequestMessage(packet, existingSession,
                            MessageContext.messageHotDataTtlMillis())) {
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    RequestEventContextFactoryHelper.capture(packet, existingSession);
                    if (!MessageAcceptPipelineHelper.publishRequestCommand(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC,
                            message.getTo(), packet)) {
                        return;
                    }

                    MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
                    return;
                }
                if (null != existingSession && (existingSession.getProgress() > RequestSessionProgress.JOINING.value()
                        || !GroupRequestSessionWay.INVITED.value().equals(existingSession.getWay()))) {
                    log.warn("{} 和 {} 存在正在处理中的群会话请求(拒绝或同意还未结束处理)", content.getIdentity(), message.getTo());
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                GroupEntity groupEntity = repository().getGroupEntity(appKey, message.getTo());
                if (groupEntity == null) {
                    log.error("群组:{} 不存在，请检查数据！", message.getTo());
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_NOT_EXIST, message.getTo() + "群组不存在！", "GroupInviteJoinMessageBiProcessor.process", packet);
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
                if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", "GroupInviteJoinMessageBiProcessor.process", packet);
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                UserEntity userEntity = repository().getUserEntity(appKey, content.getIdentity());
                if (userEntity == null) {
                    log.error("用户:{} 不存在，请检查数据！", content.getIdentity());
                    ExceptionReporter.reportBusiness(ExceptionCodeEnum.USER_NOT_EXIST, content.getIdentity() + "用户不存在！", "GroupInviteJoinMessageBiProcessor.process", packet);
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                    return;
                }
                // 热会话可能已写入，而 QoS COMMIT 的结果尚未确认。同一 messageId 应续做首次命令，
                // 不能按当前邀请策略重新推导 PENDING/AGREE，否则自动接受后待审批的邀请无法重试。
                boolean sameInvite = existingSession != null
                        && message.getId().equals(existingSession.getSessionId())
                        && message.getFrom().equals(existingSession.getInviter());
                if (existingSession != null && (!message.getFrom().equals(existingSession.getInviter())
                        || (!sameInvite && !GroupJoinerProcessStatus.PENDING.value()
                        .equals(existingSession.getJoinerProcessStatus())))) {
                    MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.REQUEST_SESSION_PROGRESS_MISMATCH);
                    return;
                }
                if (sameInvite) {
                    List<Packet> stored = repository().getPackets(appKey, List.of(packet.getPacketId()));
                    Packet first = stored == null || stored.size() != 1 ? null : stored.getFirst();
                    if (first == null || first.getMessage() == null || first.getMessage().getMetadata() == null
                            || first.getMessage().getMetadata().getRequestEventContext() == null) {
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    // 被邀请人或管理员已改变会话时，不得用原邀请重试覆盖其决定。
                    var firstState = first.getMessage().getMetadata().getRequestEventContext();
                    if (!Objects.equals(first.getMessage().getId(), message.getId())
                            || !Objects.equals(first.getMessage().getFrom(), message.getFrom())
                            || !Objects.equals(first.getMessage().getTo(), message.getTo())
                            || !Objects.equals(firstState.getProgress(), existingSession.getProgress())
                            || !Objects.equals(firstState.getJoinerProcessStatus(),
                            existingSession.getJoinerProcessStatus())) {
                        MessageSubmissionResponseHelper.rejected(ctx, packet,
                                ExceptionCodeEnum.REQUEST_SESSION_PROGRESS_MISMATCH);
                        return;
                    }
                    // 已保存的会话代表第一次决策；QoS claim 已在 preProcess 获取，重新热写后完成领域命令。
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), existingSession)) {
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }
                    RequestEventContextFactoryHelper.capture(packet, existingSession);
                    if (MessageAcceptPipelineHelper.publishRequestCommand(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC,
                            message.getTo(), packet)) {
                        MessageAcceptPipelineHelper.requestAccepted(ctx, packet);
                    }
                    return;
                }
                GroupRequestSession groupRequestSession = existingSession != null ? existingSession
                        : GroupRequestSession.newGroupBuilder()
                                .sessionId(message.getId())
                                .joiner(content.getIdentity())
                                .groupId(message.getTo())
                                .inviter(message.getFrom())
                                .way(GroupRequestSessionWay.INVITED.value())
                                .channel(GroupRequestSessionChannel.OTHER.value())
                                .progress(RequestSessionProgress.JOINING.value())
                                .joinerProcessStatus(GroupJoinerProcessStatus.PENDING.value())
                                .build();

                boolean inviterIsMannerOrLeader = false;
                Double inviterPost = groupMannerOrLeaderUsersIdentityAndPostMap.remove(message.getFrom());
                if (inviterPost == null) {
                    GroupUserEntity fromGroupUserEntity = repository().groupUserEntity(appKey, message.getTo(), message.getFrom());
                    if (fromGroupUserEntity == null) {
                        log.error("群组：{}, 用户：{} 不存在，请检查数据！", message.getTo(), message.getFrom());
                        ExceptionReporter.reportBusiness(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, message.getFrom() + "不在群组中！", "GroupInviteJoinMessageBiProcessor.process", packet);
                        MessageSubmissionResponseHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                        return;
                    }
                    groupRequestSession.setInviterPost(fromGroupUserEntity.getPost());
                } else {
                    inviterIsMannerOrLeader = true;
                    groupRequestSession.setInviterPost(inviterPost.intValue());
                }
                boolean canSkipAdminReview = inviterIsMannerOrLeader
                        || GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy());

                if (GroupInvitePolicy.AUTO_PASS.value().equals(userEntity.getGroupInvitePolicy())) {
                    groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                    if (canSkipAdminReview) {
                        groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                        if (inviterIsMannerOrLeader) {
                            groupRequestSession.setProcessor(message.getFrom());
                            groupRequestSession.setProcessorPost(groupRequestSession.getInviterPost());
                        }
                        if (!GroupBindResultHelper.acceptedOrReply(ctx, packet,
                                repository().autoPassBindGroup(packet, groupRequestSession,
                                        MessageContext.messageHotDataTtlMillis(),
                                        GroupBindResultHelper.maxMembers(), GroupBindResultHelper.maxPerUser()),
                                "自动绑定群组请求消息异常!")) {
                            return;
                        }

                    } else {
                        groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                        if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), groupRequestSession)) {
                            log.error("Failed to save invite join group request message: {}", packet);
                            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", "GroupInviteJoinMessageBiProcessor.process", packet);
                            MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                            return;
                        }

                    }
                } else {
                    groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.PENDING.value());
                    groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), groupRequestSession)) {
                        log.error("Failed to save invite join group request message: {}", packet);
                        ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", "GroupInviteJoinMessageBiProcessor.process", packet);
                        MessageSubmissionResponseHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        return;
                    }

                }
                RequestEventContextFactoryHelper.capture(packet, groupRequestSession);
                if (!MessageAcceptPipelineHelper.publishRequestCommand(ctx, MqConstant.MQ_GROUP_REQUEST_TOPIC, message.getTo(), packet)) {
                    return;
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

}
