package com.ouyunc.message.processor;

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
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.GroupValidator;
import com.ouyunc.message.validator.PermissionValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * 主动加群
 */
public final class GroupJoinMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupJoinMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP_REQUEST_JOIN;
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
                        .verify(packet, ctx)
                        .onErrorResume(error -> {
                            log.error("校验过程中出现异常: {}", error.getMessage());
                            return Mono.just(true); // 出现异常时默认校验不通过
                        }).flatMap(result -> {
                            if (result) {
                                log.warn("权限不足/在黑名单中/群异常（被平台封禁）, 请知悉。该消息 {} 被忽略", packet);
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
            log.debug("GroupJoinMessageProcessor 正在处理外部客户端加群 {} ...", packet);
        }
        // 1. 保存消息
        Message message = packet.getMessage();
        // 这里不保存到session 缓存中,保存到临时的会话消息中，该好友请求的消息可以对其进行定期清理；
        // 获取当前群的管理员和群主，进行加群消息的推送
        Set<String> groupMannerOrLeaderUsersIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupMannerOrLeaderUsersIdentitySet)) {
            // 这个群里没有群主？
            log.error("群组：{}, 不存在群主或群管理员！群消息： {}", packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群主或群管理员", packet), true);
            return;
        }
        String sessionId = packet.getMessage().getTo();
        // 保存消息和离线消息记录
        if (!saveGroupRequestMessage(packet, groupMannerOrLeaderUsersIdentitySet)) {
            log.error("Failed to save  group join request message: {}", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存加群请求消息异常!", packet), true);
            return;
        }
        repository().savePacket2Mq(MqConstant.KAFKA_GROUP_REQUEST_TOPIC, sessionId, packet).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("加群请求，发送mq异常，原因：{}", ex.getMessage());
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理加群请求异常！" + ex.getMessage(), packet), true);
            } else {
                // 如果有群主或群管理员，则发送消息给群主或群管理员
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
    }


    /**
     * 保存群组消息
     */
    private boolean saveGroupRequestMessage(Packet packet, Set<String> groupMembers) {
        Message message = packet.getMessage();
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel() ? saveQosMessage(packet, groupMembers) : saveNonQosMessage(packet, message.getTo())) {
            return true;
        }
        log.error("Failed to save group join message: {}", packet);
        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存到缓存加群消息异常!", packet), true);
        return false;
    }


    /**
     * 保存Qos消息
     *
     * @param packet
     * @param groupMembers
     * @return
     */
    private boolean saveQosMessage(Packet packet, Set<String> groupMembers) {
        return repository().batchSaveGroupRequestMessage(
                packet,
                groupMembers,
                MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP
        );
    }

    /**
     * 保存非Qos消息
     *
     * @param packet
     * @param sessionId
     * @return
     */
    private boolean saveNonQosMessage(Packet packet, String sessionId) {
        return repository().saveGroupRequestMessage(
                packet,
                sessionId,
                MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP
        );
    }

}
