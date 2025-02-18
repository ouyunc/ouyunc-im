package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.core.listener.event.WithdrawMessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;


/**
 * 撤销消息处理器,
 */
public class WithdrawMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.WITHDRAW_CONTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("WithdrawMessageProcessor 正在处理撤销消息...");
        Message message = packet.getMessage();
        // 获取需要撤销的消息id，（这里使用String类型接收）
        List<String> packetIds = JSON.parseArray(message.getContent(), String.class);
        if (CollectionUtils.isEmpty(packetIds)) {
            log.warn("WithdrawMessageProcessor 处理撤销消息异常，packetIds: {} 为空", packetIds);
            return;
        }
        // 判断是单聊还是群聊; 注意： 这里只针对单聊和群聊的业务做该内容类型的处理
        if (MessageTypeEnum.ONE_2_ONE.getType() == packet.getMessageType()) {
            // 保存消息， 30 天 过期， 后面通过配置文件进行可配置
            if (!repository().saveMessage(packet, IdentityUtil.sessionId(message.getFrom(), message.getTo()), NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                log.error("保存撤回消息: {} 异常", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存撤回一对一消息异常!", packet), true);
                return;
            }
            // 保存到磁盘
            MessageServerContext.publishEvent(new SaveMessageEvent(packet), true);
            // 处理撤销消息
            if (!repository().withdrawMessage(packetIds)) {
                // 未撤销成功
                log.error("WithdrawMessageProcessor 处理撤销消息失败，packetIds: {}", packetIds);
                return;
            }
            // 撤销磁盘的消息
            MessageServerContext.publishEvent(new WithdrawMessageEvent(packet), true);
            // 发布消息
            List<LoginClientInfo> loginClientInfos = ClientHelper.onlineAll(message.getTo(), message.getTo());
            if (CollectionUtils.isEmpty(loginClientInfos)) {
                log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
                return;
            }
            // 异步每个多设备在线段发送消息
            MessageHelper.asyncSendMessage(packet, loginClientInfos);

        }else if (MessageTypeEnum.GROUP.getType() == packet.getMessageType()) {
            // 如果不需要QOS，或者该消息qos 等于0，则直接返回,则不需要存储到离线数据，则是需要存储一份群会话消息；这种情况走读扩散
            if (!MessageContext.messageProperties.isQosEnable() || message.getQos() == QosLevelEnum.QOS_0.getLevel()) {
                // 保存消息， 30 天 过期， 后面通过配置文件进行可配置
                if (!repository().saveMessage(packet, message.getTo(), NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                    log.error("群消息: {} 保存撤回非qos质量消息异常", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存撤回群息异常!", packet), true);
                    return;
                }
            }else {
                // 如果开启了QOS，且该消息需要QOS，则需要存储离线消息给到每个群成员；注意这种情况下就走写扩散了
                // 获取群成员的唯一登录标识，进行发送消息 @todo 使用lua 脚本批量执行
                // 批量保存消息
                if (!repository().saveMessage(packet, message.getTo(), NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP)) {
                    log.error("群消息: {} 保存撤回qos质量消息异常", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,"保存撤回群息异常!", packet), true);
                    return;
                }
            }
            // 处理撤销消息 @todo 批量撤回
            if (!repository().withdrawMessage(packetIds)) {
                // 未撤销成功
                log.error("WithdrawMessageProcessor 处理撤销消息失败，packetIds: {}", packetIds);
                return;
            }
            // 发布消息
            // 获取群组成员登录标识id
            Set<String> groupUserIdentitySet = repository().groupUsersIdentity(packet);
            // 保存成功后，进行发送消息
            for (String groupUserIdentity : groupUserIdentitySet) {
                // 获取群成员的唯一登录标识，进行发送消息
                List<LoginClientInfo> groupUserLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), groupUserIdentity);
                // 如果不在线的话，先保存到离线消息队列中，然后发送消息到对方
                if (CollectionUtils.isEmpty(groupUserLoginClientInfos)) {
                    log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
                    continue;
                }
                // 异步给群成员的每个在线段发送消息
                MessageHelper.asyncSendMessage(packet, groupUserLoginClientInfos);
            }
        }else {
            log.error("暂不支持该消息类型：{} 的消息撤回", packet.getMessageType());
        }
    }
}
