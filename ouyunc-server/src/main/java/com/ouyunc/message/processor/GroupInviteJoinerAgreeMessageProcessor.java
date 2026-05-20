package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.constants.GroupJoinerProcessStatus;
import com.ouyunc.domain.constants.GroupRequestSessionWay;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 被邀请人同意加群后，通知群主或管理员进行同意或拒绝， 不通知邀请人（除非邀请人是管理员或群主，如果邀请人是管理员或群主，则被邀请人同意后会自动加群，不用等待其他人去处理加群了）
 * */
public final class GroupInviteJoinerAgreeMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinerAgreeMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_AGREE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex == null) {
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
                    log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
                    ctx.close();
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
            } else {
                // 发送失败
                log.error("Failed to send message: {} ", ex.getMessage());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
            }
        });
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinerAgreeMessageProcessor 正在处理被邀请加群者同意加群的请求 {} ...", packet);
        }
        Message message = packet.getMessage();
        String joiner = message.getFrom();
        String appKey = message.getMetadata().getAppKey();
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, joiner, message.getTo());

        runWithDistributedLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, joiner, message.getTo());
            if (null == groupRequestSession || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()) || StringUtils.isBlank(groupRequestSession.getInviter()) || !Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.PENDING.value())) {
                log.warn("{} 和 {} 不存在正在处理中的群会话请求或当前群请求不是邀请或邀请人为空或存在拒绝或同意还未结束处理", joiner, message.getTo());
                return;
            }
            if (repository().inGroup(appKey, joiner, message.getTo())) {
                log.warn("该用户 {} 已经加入群组 {}", joiner, message.getTo());
                return;
            }
            Set<String> groupMannerOrLeaderUsersIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
            if (CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
                log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom())) {
                log.error("发送者管理员或群主：{} 不允许处理，已经存在群组中了", message.getFrom());
                return;
            }
            groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
            if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession)) {
                log.error("Failed to save invited join group agree request message: {}", packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存被邀请同意加群请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("同意邀请加群请求，发送mq异常，原因：{}", ex.getMessage());
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "被邀请人同意邀请加群请求异常！" + ex.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    return;
                }
                List<String> notifyIdentities = new ArrayList<>(groupMannerOrLeaderUsersIdentitySet);
                if (!notifyIdentities.contains(groupRequestSession.getInviter())) {
                    notifyIdentities.add(groupRequestSession.getInviter());
                }
                ctx.channel().eventLoop().execute(() -> deliverOnlineAndFireNext(ctx, packet, message.getMetadata().getAppKey(), notifyIdentities));
            });
        });
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
