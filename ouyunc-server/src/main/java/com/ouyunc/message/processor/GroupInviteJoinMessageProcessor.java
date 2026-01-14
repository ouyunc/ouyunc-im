package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
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
import com.ouyunc.domain.constants.*;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 邀请加群
 */
public final class GroupInviteJoinMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupInviteJoinMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN;
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
                        .or(GroupUserValidator.INSTANCE.negate())
                        .verify(packet, ctx)
                        .onErrorResume(error -> {
                            log.error("校验过程中出现异常: {}", error.getMessage());
                            return Mono.just(true); // 出现异常时默认校验不通过
                        }).flatMap(result -> {
                            if (result) {
                                log.warn("权限不足/在黑名单中/群异常（被平台封禁）/不是群成员/接受者和发送者相同, 请知悉。该消息 {} 被忽略", packet);
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
            log.debug("GroupInviteJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        // 1. 保存消息
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        }else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            return;
        }
        // 加锁
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.buildGroupRequestLockCacheKey(appKey, content.getIdentity(), message.getTo()));
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 获取请求会话
                GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
                if (null != groupRequestSession && (groupRequestSession.getProgress() > RequestSessionProgress.JOINING.value() || !GroupRequestSessionWay.INVITED.value().equals(groupRequestSession.getWay()))) {
                    log.warn("{} 和 {} 存在正在处理中的群会话请求(拒绝或同意还未结束处理)", content.getIdentity(), message.getTo());
                    return;
                }
                // 如果发送方和加入方相同，则不允许操作
                if (message.getFrom().equals(content.getIdentity())) {
                    log.warn("发送方: {} 和加入方: {} 相同，忽略 该请求",  message.getFrom(), content.getIdentity());
                    return;
                }
                // 判断是否已经加入群组
                if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
                    log.warn("该用户 {} 已经加入群组 {}", content.getIdentity(), message.getTo());
                    return;
                }
                // 获取群的配置信息，判断是否自动通过
                GroupEntity groupEntity = repository().getGroupEntity(appKey, message.getTo());
                if (groupEntity == null) {
                    log.error("群组:{} 不存在，请检查数据！", message.getTo());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_NOT_EXIST, message.getTo() + "群组不存在！", packet));
                    return;
                }
                // 这里不保存到session 缓存中,保存到临时的会话请求消息中，该好友请求的消息可以对其进行定期清理；
                // 获取当前群的管理员和群主，进行加群消息的推送
                Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
                if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                    // 这个群里没有群主？
                    log.error("群组：{}, 不存在群主和群管理员！群消息： {}", packet.getMessage().getTo(), packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), true);
                    return;
                }
                // 判断被邀请人，是否自动被加入到群
                UserEntity userEntity = repository().getUserEntity(appKey, content.getIdentity());
                if (userEntity == null) {
                    log.error("用户:{} 不存在，请检查数据！", content.getIdentity());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.USER_NOT_EXIST, content.getIdentity() + "用户不存在！", packet));
                    return;
                }
                // 尝试设置群请求会话
                boolean inviterIsMannerOrLeader = false;
                if (groupRequestSession == null) {
                    groupRequestSession = GroupRequestSession.newGroupBuilder()
                            .sessionId(MessageContext.idGenerator().generateIdStr())
                            .joiner(content.getIdentity())
                            .inviter(message.getFrom())
                            .groupId(message.getTo())
                            .channel(GroupRequestSessionChannel.OTHER.value())
                            .way(GroupRequestSessionWay.INVITED.value())
                            .build();
                }
                // 尝试移除自己
                Double inviterPost = groupMannerOrLeaderUsersIdentityAndPostMap.remove(message.getFrom());
                if (inviterPost == null) {
                    GroupUserEntity fromGroupUserEntity = repository().groupUserEntity(appKey, message.getTo(), message.getFrom());
                    if (fromGroupUserEntity == null) {
                        log.error("群组：{}, 用户：{} 不存在，请检查数据！", message.getTo(), message.getFrom());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, message.getFrom() + "不在群组中！", packet));
                        return;
                    }
                    groupRequestSession.setInviterPost(fromGroupUserEntity.getPost());
                }else {
                    inviterIsMannerOrLeader = true;
                    groupRequestSession.setInviterPost(inviterPost.intValue());
                }
                // 尝试排除自己，自己可能是群主或管理员
                // 注意：如果是群主或者管理员邀请的会自动通过，无论是否开启群自动同意
                // 判断对方是否是自动同意加好友
                if ((inviterIsMannerOrLeader || GroupJoinPolicy.AUTO_PASS.value().equals(groupEntity.getGroupJoinPolicy())) && GroupInvitePolicy.AUTO_PASS.value().equals(userEntity.getGroupInvitePolicy())) {
                    groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.AGREE.value());
                    groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
                    // 自动同意，不再给群主和管理员保存离线消息
                    if (!repository().autoPassBindGroup(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        log.error("自动处理绑定群组失败: {}", packet);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "自动绑定群组请求消息异常!", packet), true);
                        return;
                    }
                }else {
                    groupRequestSession.setJoinerProcessStatus(GroupJoinerProcessStatus.PENDING.value());
                    groupRequestSession.setProgress(RequestSessionProgress.JOINING.value());
                    if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentityAndPostMap.keySet(), groupRequestSession)) {
                        log.error("Failed to save invite join group request message: {}", packet);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", packet), true);
                        return;
                    }
                }
                // 发送mq
                repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("邀请加群请求，发送mq异常，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理邀请加群请求异常！" + ex.getMessage(), packet), true);
                    } else {
                        // 发送给被邀请人
                        repository().reactiveSaveOfflineMessage(packet,  content.getIdentity(), MessageServerContext.deviceTypeList(message.getMetadata().getAppKey(), content.getIdentity())).subscribe(saveResult ->  {
                            if (saveResult) {
                                // 被邀请者
                                List<LoginClientInfo> inviterLoginClientInfos = ClientHelper.onlineAll(packet.getMessage().getMetadata().getAppKey(), content.getIdentity());
                                if (CollectionUtils.isNotEmpty(inviterLoginClientInfos)) {
                                    MessageHelper.asyncSendMessage(packet, inviterLoginClientInfos);
                                }
                                // 如果有群主或群管理员，则发送消息给群主或群管理员
                                groupMannerOrLeaderUsersIdentityAndPostMap.forEach((groupMannerOrLeaderUserIdentity, groupMannerOrLeaderUserPost) -> {
                                    List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupMannerOrLeaderUserIdentity);
                                    if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                                        MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                                    }
                                });
                                // 处理成功则转到下个处理器
                                ctx.fireChannelRead(packet);
                            } else {
                                log.error("保存被邀请者离线消息失败: {}", packet);
                                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.SAVE_OFFLINE_MESSAGE_ERROR, "保存被邀请者离线消息异常！", packet), true);
                            }
                        });
                    }
                });
            } else {
                log.error("Failed to lock user invite join group request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "邀请加群请求锁失败", packet), true);
            }
        } catch (Exception e) {
            log.error("Failed to handle user invite join group request message: {}", e.getMessage());
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_GROUP_ERROR, "处理邀请加群请求异常！" + e.getMessage(), packet), true);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }else {
                log.error("Failed to unlock user invite join group request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.UN_LOCK_ERROR, "释放锁失败", packet), true);
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
            Map<String, Collection<DeviceType>> groupMemberDeviceTypes = new HashMap<>();
            for (String groupMember : groupMembers) {
                groupMemberDeviceTypes.put(groupMember, MessageServerContext.deviceTypeList(message.getMetadata().getAppKey(), groupMember));
            }
            return repository().batchSaveJoinGroupRequestMessage(packet, groupMemberDeviceTypes, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
        }
        // 保存，不需要qos
        return repository().saveJoinGroupRequestMessage(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }

}
