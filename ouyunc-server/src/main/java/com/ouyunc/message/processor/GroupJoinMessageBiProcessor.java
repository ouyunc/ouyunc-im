package com.ouyunc.message.processor;

import com.ouyunc.base.constant.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;

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
                    .or(BlackListValidator.INSTANCE)
                    .or(GroupValidator.INSTANCE)
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/在黑名单中/群异常（被平台封禁）/接收者和发送者相同, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, message.getFrom(), message.getTo());

        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            GroupRequestSession existingSession = repository().getGroupRequestSession(appKey, message.getFrom(), message.getTo());
            if (null != existingSession && (existingSession.getProgress() > RequestSessionProgress.JOINING.value() || !GroupRequestSessionWay.ACTIVE.value().equals(existingSession.getWay()))) {
                log.warn("{} 和 {} 会话请求存在正在处理中的群请求，拒绝或同意还未结束处理", message.getFrom(), message.getTo());
                return;
            }
            if (repository().inGroup(appKey, message.getFrom(), message.getTo())) {
                log.warn("该用户 {} 已经加入群组 {}", message.getFrom(), message.getTo());
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
            Set<String> notifyManagerAndLeaderUserIds = new HashSet<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
            Set<String> groupMannerOrLeaderUsersIdentitySet = new HashSet<>(notifyManagerAndLeaderUserIds);
            if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom()) || CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
                log.error("群组：{}, 不存在群主和群管理员或群消息或已经加入群组： {}", packet.getMessage().getTo(), packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            GroupRequestSession groupRequestSession = existingSession != null ? existingSession
                    : GroupRequestSession.newGroupBuilder()
                            .sessionId(MessageContext.idGenerator().generateIdStr())
                            .joiner(message.getFrom())
                            .groupId(message.getTo())
                            .channel(GroupRequestSessionChannel.OTHER.value())
                            .way(GroupRequestSessionWay.ACTIVE.value())
                            .build();
            groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
            groupRequestSession.setWay(GroupRequestSessionWay.ACTIVE.value());

            Set<String> notifyIdentities;
            if (GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy())) {
                groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                if (!repository().autoPassBindGroup(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                    log.error("群已开启自动同意，主动加群绑定失败: {}", packet);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "自动绑定群组请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                    return;
                }
                notifyIdentities = RequestNotifyHelper.userOnly(message.getFrom());
            } else {
                groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession, existingSession != null)) {
                    log.error("Failed to save join group request message: {}", packet);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                    return;
                }
                notifyIdentities = RequestNotifyHelper.copyOf(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
            }
            RequestNotifyHelper.dispatch(ctx, packet, appKey, notifyIdentities);
            repository().publishPacketAsync(MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet,
                    "处理加群请求 MQ 旁路");
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

}
