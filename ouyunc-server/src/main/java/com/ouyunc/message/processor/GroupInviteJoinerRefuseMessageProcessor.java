package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.constants.GroupJoinerProcessStatus;
import com.ouyunc.domain.constants.GroupRequestSessionWay;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 被邀请人同意加群后，通知群主或管理员进行同意或拒绝， 不通知邀请人（除非邀请人是管理员或群主，如果邀请人是管理员或群主，则被邀请人同意后会自动加群，不用等待其他人去处理加群了）
 */
public final class GroupInviteJoinerRefuseMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinerRefuseMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_REFUSE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex == null) {
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
                    log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), true);
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
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), true);
            }
        });
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupInviteJoinerRefuseMessageProcessor 正在处理被邀请加群者同意加群的请求 {} ...", packet);
        }
        // 1. 保存消息
        Message message = packet.getMessage();
        String joiner = message.getFrom();
        String appKey = message.getMetadata().getAppKey();
        // 加锁
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + joiner + CacheConstant.COLON + message.getTo());
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 获取请求会话
                GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, joiner, message.getTo());
                if (null == groupRequestSession || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()) || StringUtils.isBlank(groupRequestSession.getInviter()) || !Objects.equals(groupRequestSession.getJoinerProcessStatus(), GroupJoinerProcessStatus.PENDING.value())) {
                    log.warn("{} 和 {} 不存在正在处理中的群会话请求或当前群请求不是邀请或邀请人为空或存在拒绝或同意还未结束处理", joiner, message.getTo());
                    return;
                }
                // 判断是否已经加入群组
                if (repository().inGroup(appKey, joiner, message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}", joiner, message.getTo());
                    return;
                }
                // 这里不保存到session 缓存中,保存到临时的会话请求消息中，该好友请求的消息可以对其进行定期清理；
                // 获取当前群的管理员和群主，进行加群消息的推送
                Set<String> groupMannerOrLeaderUsersIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
                if (CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
                    // 这个群里没有群主？
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), true);
                    return;
                }
                // 如果有群主或群管理员，则发送消息给群主或群管理员， 排除自己,如果返回false 说明处理人不是管理员
                if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom())) {
                    log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
                    return;
                }
                // 更新缓存groupRequestSession 的处理状态为同意， mongo 中状态更新通过mq来监听更新, 判断如果邀请人是群主或管理员，则自动同意，添加好友
                groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.REFUSE.value());
                if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession)) {
                    log.error("Failed to save invited join group refuse request message: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存被邀请拒绝加群请求消息异常!", packet), true);
                    return;
                }
                // 发送mq
                repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("邀请拒绝加群请求，发送mq异常，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "被邀请人拒绝邀请加群请求异常！" + ex.getMessage(), packet), true);
                    } else {
                        // 需要给邀请者发送消息？
                        if (groupMannerOrLeaderUsersIdentitySet.contains(groupRequestSession.getInviter())) {
                            // 如果有群主或群管理员，则发送消息给群主或群管理员
                            for (String groupMannerOrLeaderUserIdentity : groupMannerOrLeaderUsersIdentitySet) {
                                List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupMannerOrLeaderUserIdentity);
                                if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                                    MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                                }
                            }
                            // 处理成功则转到下个处理器
                            ctx.fireChannelRead(packet);
                        }else {
                            repository().reactiveSaveOfflineMessage(packet,  groupRequestSession.getInviter()).subscribe(saveResult ->  {
                                if (saveResult) {
                                    // 邀请人
                                    List<LoginClientInfo> inviterLoginClientInfos = ClientHelper.onlineAll(packet.getMessage().getMetadata().getAppKey(), groupRequestSession.getInviter());
                                    if (CollectionUtils.isNotEmpty(inviterLoginClientInfos)) {
                                        MessageHelper.asyncSendMessage(packet, inviterLoginClientInfos);
                                    }
                                    // 如果有群主或群管理员，则发送消息给群主或群管理员
                                    for (String groupMannerOrLeaderUserIdentity : groupMannerOrLeaderUsersIdentitySet) {
                                        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupMannerOrLeaderUserIdentity);
                                        if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                                            MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                                        }
                                    }
                                    // 处理成功则转到下个处理器
                                    ctx.fireChannelRead(packet);
                                } else {
                                    log.error("保存被邀请者离线消息失败: {}", packet);
                                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.SAVE_OFFLINE_MESSAGE_ERROR, "保存被邀请者离线消息异常！", packet), true);
                                }
                            });
                        }
                    }
                });
            } else {
                log.error("Failed to lock user invite join group refuse request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "拒绝邀请加群请求锁失败", packet), true);
            }
        } catch (Exception e) {
            log.error("Failed to handle user invite join group refuse request message: {}", e.getMessage());
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_GROUP_ERROR, "处理拒绝邀请加群请求异常！" + e.getMessage(), packet), true);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, GroupRequestSession groupRequestSession) {
        Message message = packet.getMessage();
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
            // 保存需要qos
            return repository().batchSaveGroupRequestMessage(packet, groupMembers, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
        }
        // 保存，不需要qos
        return repository().saveGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
