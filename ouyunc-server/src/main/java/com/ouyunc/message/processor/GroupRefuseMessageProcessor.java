package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Sets;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.constants.GroupRequestSessionWay;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 拒绝 加/邀请 群
 */
public final class GroupRefuseMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupRefuseMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_REFUSE;
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
                        .or(BlackListValidator.INSTANCE)
                        .or(GroupValidator.INSTANCE)
                        .or(GroupUserValidator.INSTANCE.negate())
                        .verify(packet, ctx)
                        .onErrorResume(error -> {
                            log.error("校验过程中出现异常: {}", error.getMessage());
                            return Mono.just(true); // 出现异常时默认校验不通过
                        }).flatMap(result -> {
                            if (result) {
                                log.warn("权限不足/在黑名单中/群异常（被平台封禁)/不是群成员, 请知悉。该消息 {} 被忽略", packet);
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
            log.debug("GroupRefuseMessageProcessor 正在处理外部客户端拒绝加群 {} ...", packet);
        }
        // 1. 保存消息
        Message message = packet.getMessage();
        String from = message.getFrom();
        // 判断消息内容类型是否是群请求类型
        if (MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType() != message.getContentType()) {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            return;
        }
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        }else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        // 加锁
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + content.getIdentity() + CacheConstant.COLON + message.getTo());
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 获取请求会话
                GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
                if (null == groupRequestSession || groupRequestSession.getProgress() != MessageConstant.REQUEST_PROGRESS_JOINING) {
                    log.warn("{} 和 {} 会话请求不存在正在处理中的群请求，或者存在有还未结束的同意或拒绝处理", content.getIdentity(), message.getTo());
                    return;
                }
                // 校验群会话请求方式
                GroupRequestSessionWay way = GroupRequestSessionWay.valueOf(groupRequestSession.getWay());
                if (way == null) {
                    log.error("非法群会话请求方式：{}", groupRequestSession.getWay());
                    return;
                }
                if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}", content.getIdentity(), message.getTo());
                    return;
                }
                // 获取当前群的管理员和群主，进行加群消息的推送
                Set<String> groupMannerOrLeaderUsersIdentitySet = Sets.newHashSet();
                Set<GroupUserEntity> groupManagerAndLeaderUserEntitySet = repository().groupManagerAndLeaderUserEntity(packet);
                GroupUserEntity fromUserEntity = null;
                for (GroupUserEntity groupUserEntity : groupManagerAndLeaderUserEntitySet) {
                    String userIdentity = groupUserEntity.getUserId().toString();
                    groupMannerOrLeaderUsersIdentitySet.add(userIdentity);
                    if (from.equals(userIdentity)) {
                        fromUserEntity = groupUserEntity;
                    }
                }
                if (CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
                    // 这个群里没有群主或者群管理员，群里面必须有且仅有一个群主
                    log.error("群组：{}, 不存在群主！群消息： {}", packet.getMessage().getTo(), packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主和群管理员", packet), true);
                    return;
                }
                if (fromUserEntity == null) {
                    log.error("处理人：{} 在群中不是管理员或群主，没有权限处理", from);
                    return;
                }
                // 如果有群主或群管理员，则发送消息给群主或群管理员， 排除自己,如果返回false 说明处理人不是管理员
                if (groupMannerOrLeaderUsersIdentitySet.remove(message.getFrom())) {
                    log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
                    return;
                }
                // 保存请求信息
                if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet, groupRequestSession.getSessionId())) {
                    log.error("Failed to save  refuse group request message: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存拒绝加群请求消息异常!", packet), true);
                    return;
                }
                // 发送mq
                repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("拒绝加群请求，发送mq异常，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理拒绝加群请求异常！" + ex.getMessage(), packet), true);
                    } else {
                        for (String groupMannerOrLeaderUserIdentity : groupMannerOrLeaderUsersIdentitySet) {
                            List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupMannerOrLeaderUserIdentity);
                            if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                                MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                            }
                        }
                        // 处理成功则转到下个处理器
                        ctx.fireChannelRead(packet);
                    }
                });
            } else {
                log.error("Failed to lock user refuse group request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "拒绝群请求加锁失败", packet), true);
            }
        } catch (Exception e) {
            log.error("Failed to handle user refuse group request message: {}", e.getMessage());
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_GROUP_ERROR, "处理拒绝主动加群/邀请加群请求异常！" + e.getMessage(), packet), true);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }



    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers, String groupRequestSessionId) {
        Message message = packet.getMessage();
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
            // 保存需要qos
            return repository().batchSaveJoinGroupRequestMessage(packet, GroupRequestSessionWay.ACTIVE, groupMembers, groupRequestSessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
        }
        // 保存，不需要qos
        return repository().saveJoinGroupRequestMessage(packet, GroupRequestSessionWay.ACTIVE, groupRequestSessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
