package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.constant.enums.GroupJoinerProcessStatus;
import com.ouyunc.base.constant.enums.GroupRequestSessionWay;
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

import java.util.*;

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
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet);
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return;
        }
        // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）群是否被封禁，是否全体禁言
        PermissionValidator.INSTANCE.negate()
                    .or(FromToValidator.INSTANCE)
                    .or(BlackListValidator.INSTANCE)
                    .or(GroupValidator.INSTANCE)
                    .or(GroupUserValidator.INSTANCE)
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/在黑名单中/群异常（被平台封禁）/已经是群成员/接受者和发送者相同, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinerRefuseMessageProcessor 正在处理被邀请加群者同意加群的请求 {} ...", packet);
        }
        Message message = packet.getMessage();
        String joiner = message.getFrom();
        String appKey = message.getMetadata().getAppKey();
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, joiner, message.getTo());

        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, joiner, message.getTo());
            if (null == groupRequestSession || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()) || StringUtils.isBlank(groupRequestSession.getInviter()) || !Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.PENDING.value())) {
                log.warn("{} 和 {} 不存在正在处理中的群会话请求或当前群请求不是邀请或邀请人为空或存在拒绝或同意还未结束处理", joiner, message.getTo());
                return;
            }
            if (repository().inGroup(appKey, joiner, message.getTo())) {
                log.warn("该用户 {} 已经加入群组 {}", joiner, message.getTo());
                return;
            }
            Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
            if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            Set<String> groupMannerOrLeaderUsersIdentitySet = new HashSet<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
            if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom())) {
                log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
                return;
            }
            groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.REFUSE.value());
            if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession)) {
                log.error("Failed to save invited join group refuse request message: {}", packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存被邀请拒绝加群请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.copyOf(groupMannerOrLeaderUsersIdentityAndPostMap.keySet()));
            repository().publishPacketAsync(MqConstant.MQ_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet,
                    "被邀请人拒绝邀请加群请求 MQ 旁路");
        });
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
