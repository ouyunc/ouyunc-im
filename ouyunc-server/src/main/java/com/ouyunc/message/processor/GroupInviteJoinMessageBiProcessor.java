package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * 邀请加群：发起方（邀请人）不推送；待被邀请人确认时仅通知被邀请人；待管理员审时仅通知群主/管理员；免审入群仅通知被邀请人。
 */
public final class GroupInviteJoinMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        ThreadPoolManager.messageProcessorExecutor().execute(() -> repository().save(packet));
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (qosDupAlreadyHandled(ctx, packet)) {
            return;
        }
        // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）群是否被封禁，是否全体禁言
        PermissionValidator.INSTANCE.negate()
                    .or(FromToValidator.INSTANCE)
                    .or(GroupInviteSelfValidator.INSTANCE)
                    .or(BlackListValidator.INSTANCE)
                    .or(GroupValidator.INSTANCE)
                    .or(GroupUserValidator.INSTANCE.negate())
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/在黑名单中/群异常（被平台封禁）/不是群成员/不能邀请自己/接受者和发送者相同, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        } else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            return;
        }
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, content.getIdentity(), message.getTo());

        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            GroupRequestSession existingSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
            if (null != existingSession && (existingSession.getProgress() > RequestSessionProgress.JOINING.value() || !GroupRequestSessionWay.INVITED.value().equals(existingSession.getWay()))) {
                log.warn("{} 和 {} 存在正在处理中的群会话请求(拒绝或同意还未结束处理)", content.getIdentity(), message.getTo());
                return;
            }
            if (message.getFrom().equals(content.getIdentity())) {
                log.warn("发送方: {} 和加入方: {} 相同，忽略 该请求", message.getFrom(), content.getIdentity());
                return;
            }
            if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
                log.warn("该用户 {} 已经加入群组 {}", content.getIdentity(), message.getTo());
                return;
            }
            GroupEntity groupEntity = repository().getGroupEntity(appKey, message.getTo());
            if (groupEntity == null) {
                log.error("群组:{} 不存在，请检查数据！", message.getTo());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_NOT_EXIST, message.getTo() + "群组不存在！", packet), MessageEventTypeEnum.EXCEPTION));
                return;
            }
            Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
            if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            UserEntity userEntity = repository().getUserEntity(appKey, content.getIdentity());
            if (userEntity == null) {
                log.error("用户:{} 不存在，请检查数据！", content.getIdentity());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.USER_NOT_EXIST, content.getIdentity() + "用户不存在！", packet), MessageEventTypeEnum.EXCEPTION));
                return;
            }
            GroupRequestSession groupRequestSession;
            if (existingSession != null) {
                groupRequestSession = existingSession;
                if (StringUtils.isBlank(groupRequestSession.getInviter())) {
                    groupRequestSession.setInviter(message.getFrom());
                }
            } else {
                groupRequestSession = GroupRequestSession.newGroupBuilder()
                        .sessionId(MessageContext.idGenerator().generateIdStr())
                        .joiner(content.getIdentity())
                        .inviter(message.getFrom())
                        .groupId(message.getTo())
                        .channel(GroupRequestSessionChannel.OTHER.value())
                        .way(GroupRequestSessionWay.INVITED.value())
                        .build();
            }

            boolean inviterIsMannerOrLeader = false;
            Double inviterPost = groupMannerOrLeaderUsersIdentityAndPostMap.remove(message.getFrom());
            if (inviterPost == null) {
                GroupUserEntity fromGroupUserEntity = repository().groupUserEntity(appKey, message.getTo(), message.getFrom());
                if (fromGroupUserEntity == null) {
                    log.error("群组：{}, 用户：{} 不存在，请检查数据！", message.getTo(), message.getFrom());
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, message.getFrom() + "不在群组中！", packet), MessageEventTypeEnum.EXCEPTION));
                    return;
                }
                groupRequestSession.setInviterPost(fromGroupUserEntity.getPost());
            } else {
                inviterIsMannerOrLeader = true;
                groupRequestSession.setInviterPost(inviterPost.intValue());
            }
            boolean canSkipAdminReview = inviterIsMannerOrLeader
                    || GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy());
            Set<String> notifyIdentities;
            if (GroupInvitePolicy.AUTO_PASS.value().equals(userEntity.getGroupInvitePolicy())) {
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                if (canSkipAdminReview) {
                    groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                    if (inviterIsMannerOrLeader) {
                        groupRequestSession.setProcessor(message.getFrom());
                        groupRequestSession.setProcessorPost(groupRequestSession.getInviterPost());
                    }
                    if (!repository().autoPassBindGroup(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        log.error("被邀请人自动同意且满足免审条件，绑定群组失败: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "自动绑定群组请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                        return;
                    }
                    notifyIdentities = RequestNotifyHelper.userOnly(content.getIdentity());
                } else {
                    groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), groupRequestSession)) {
                        log.error("Failed to save invite join group request message: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                        return;
                    }
                    notifyIdentities = RequestNotifyHelper.copyOf(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                }
            } else {
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.PENDING.value());
                groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), groupRequestSession)) {
                    log.error("Failed to save invite join group request message: {}", packet);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                    return;
                }
                notifyIdentities = RequestNotifyHelper.userOnly(content.getIdentity());
            }
            RequestNotifyHelper.dispatch(ctx, packet, appKey, notifyIdentities);
            repository().publishPacketAsync(MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet,
                    "处理邀请加群请求 MQ 旁路");
        });
    }


    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        return repository().saveJoinGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
