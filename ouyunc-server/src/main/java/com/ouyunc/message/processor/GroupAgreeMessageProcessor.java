package com.ouyunc.message.processor;

import com.ouyunc.base.executor.ThreadPoolManager;
import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.constants.GroupJoinerProcessStatus;
import com.ouyunc.domain.constants.GroupRequestSessionWay;
import com.ouyunc.domain.constants.RequestSessionProgress;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理人同意 加/邀请 群
 */
public final class GroupAgreeMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupAgreeMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_AGREE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        ThreadPoolManager.messageProcessorExecutor().execute(() ->
                repository().save(packet).whenComplete((ignored, ex) -> {
                    if (ex != null) {
                        log.warn("异步归档 packet 到 MQ 失败, packetId={}, 原因: {}",
                                packet.getPacketId(), ex.getMessage(), ex);
                        MessageServerContext.publishEvent(new MessageEvent(
                                ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                                        "异步归档消息到 MQ 失败: " + ex.getMessage(), packet),
                                MessageEventTypeEnum.EXCEPTION), true);
                    }
                }));
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
                    .or(GroupUserValidator.INSTANCE.negate())
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/在黑名单中/群异常（被平台封禁)/不是群成员/接受者和发送者相同, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("GroupAgreeMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        Message message = packet.getMessage();
        if (MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType() != message.getContentType()) {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            return;
        }
        Object contentObj = JSON.parseObject(message.getContent(), MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
        GroupRequestContent content;
        if (contentObj instanceof GroupRequestContent groupRequestContent) {
            content = groupRequestContent;
        } else {
            log.error("消息内容类型:{} 不是群请求类型，请检查消息内容类型是否正确", message.getContentType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MESSAGE_CONTENT_TYPE_ERROR, "消息内容类型错误", packet), MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        String lockKey = CacheConstant.buildGroupRequestLockCacheKey(appKey, content.getIdentity(), message.getTo());

        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_GROUP_ERROR, () -> {
            GroupRequestSession groupRequestSession = repository().getGroupRequestSession(appKey, content.getIdentity(), message.getTo());
            if (null == groupRequestSession || !RequestSessionProgress.JOINING.value().equals(groupRequestSession.getProgress())) {
                log.warn("{} 和 {} 会话请求不存在正在处理中的群请求，或者存在有还未结束的同意或拒绝处理", content.getIdentity(), message.getTo());
                return;
            }
            GroupRequestSessionWay way = GroupRequestSessionWay.valueOf(groupRequestSession.getWay());
            if (way == null) {
                log.error("非法群会话请求方式：{}", groupRequestSession.getWay());
                return;
            }
            if (GroupRequestSessionWay.INVITED.equals(way) && message.getFrom().equals(content.getIdentity())) {
                log.warn("发送方: {} 和加入方: {} 相同，忽略 该请求", message.getFrom(), content.getIdentity());
                return;
            }
            if (GroupRequestSessionWay.INVITED.equals(way) && !GroupJoinerProcessStatus.AGREE.value().equals(groupRequestSession.getJoinerProcessStatus())) {
                log.error("被邀请人 {} 尚未同意邀请，请等待", groupRequestSession.getJoiner());
                return;
            }
            if (repository().inGroup(appKey, content.getIdentity(), message.getTo())) {
                log.warn("该用户 {} 已经加入群组 {}", content.getIdentity(), message.getTo());
                return;
            }
            Map<String, Double> groupMannerOrLeaderUsersIdentityAndPostMap = repository().groupManagerAndLeaderUsersIdentityAndPost(packet);
            if (MapUtils.isEmpty(groupMannerOrLeaderUsersIdentityAndPostMap)) {
                log.error("群组：{}, 不存在群主！群消息： {}", packet.getMessage().getTo(), packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主和群管理员", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            if (!groupMannerOrLeaderUsersIdentityAndPostMap.containsKey(message.getFrom())) {
                log.error("处理人不是管理员或群主：{} 不允许处理", message.getFrom());
                return;
            }
            Double processorPost = groupMannerOrLeaderUsersIdentityAndPostMap.remove(message.getFrom());
            groupRequestSession.setProgress(RequestSessionProgress.AGREEING.value());
            groupRequestSession.setProcessor(message.getFrom());
            groupRequestSession.setProcessorPost(processorPost.intValue());
            if (!repository().manualPassBindGroup(packet, groupRequestSession, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                log.error("手动处理绑定群组失败: {}", packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "手动绑定群组请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, packet.getMessage().getTo(), packet).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("同意加群请求，发送mq异常，原因：{}", ex.getMessage());
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理同意加群请求异常！" + ex.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    return;
                }
                List<String> notifyIdentities = new ArrayList<>(groupMannerOrLeaderUsersIdentityAndPostMap.keySet());
                notifyIdentities.add(content.getIdentity());
                ctx.channel().eventLoop().execute(() -> {
                    if (CollectionUtils.isNotEmpty(notifyIdentities)) {
                        for (String identity : notifyIdentities) {
                            if (StringUtils.isBlank(identity)) {
                                continue;
                            }
                            List<LoginClientInfo> clients = ClientHelper.onlineAll(appKey, identity);
                            if (CollectionUtils.isNotEmpty(clients)) {
                                MessageHelper.asyncSendMessage(packet, clients);
                            }
                        }
                    }
                    ctx.fireChannelRead(packet);
                });
            });
        });
    }


}
