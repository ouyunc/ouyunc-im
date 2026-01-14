package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.processor.Processor;
import com.ouyunc.core.qos.Qos;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 基础抽象处理类
 **/
public abstract class AbstractBaseProcessor<T extends Number> implements Processor<ChannelHandlerContext, Packet>, Qos {
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseProcessor.class);

    /**
     * 类型
     */
    public abstract Type<? extends T> type();

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository> R repository() {
        return (R) DefaultRepository.INSTANCE;
    }

    /**
     * qos 前置处理，一般用于消息过滤，比如消息是否是重发等
     */
    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        // 判断是否是需要qos以及是否是客户端模式
        Message message = packet.getMessage();
        // 判断是否是客户端模式
        if (QosModeEnum.CLIENT.equals(MessageServerContext.serverProperties().getQosMode()) && packet.getMessageType() == MessageTypeEnum.QOS_DUP.getType() && message.getContentType() == MessageContentTypeEnum.QOS_DUP_CONTENT.getType()) {
            // 如果是客户端模式，判断是否需要拦截（是否是重发消息），如果是重发消息且已经发送过（存储到离线消息中），则直接返回ack，否则构造正常消息，往下传递
            Packet dupPacket = JSON.parseObject(message.getContent(), Packet.class);
            // 判断是否已经在离线消息中, 如果已经发送过，返回true,否则返回false
            if (repository().checkDup(dupPacket, MessageServerContext.deviceType(message.getMetadata().getAppKey(), packet.getDeviceType()))) {
                return true;
            }
            // 将元数据放入重发消息的packet中，否则会丢失相关信息
            Metadata metadata = message.getMetadata();
            dupPacket.getMessage().setMetadata(metadata);
            // 将重发消息的packet替换成原来的packet
            packet = dupPacket;
            log.info("qos 客户端模式正在处理客户端重发消息, 重发消息为: {}", packet);
        }
        return false;
    }

    /**
     * qos 后置处理，一般用于发送ack，给发送端确认消息已经到达服务端
     */
    @Override
    public void qosPostHandle(ChannelHandlerContext ctx, Packet packet) {
        // 这里使用默认的ack
        // 如果消息qos的级别不等于0, 目前qos = 1,2,3 没做区分
        if (packet.getMessage().getQos() > QosLevelEnum.QOS_0.getLevel()) {
            // 发送ack
            // 构造一个ack消息包
            Packet ackPacket = packet.clone();
            Message ackMessage = ackPacket.getMessage();
            Metadata metadata = ackMessage.getMetadata();
            String from = ackMessage.getFrom();
            ackMessage.setFrom(null);
            ackMessage.setTo(from);
            ackMessage.setContent(String.valueOf(packet.getPacketId()));
            ackMessage.setContentType(MessageContentTypeEnum.TEXT_CONTENT.getType());
            ackMessage.setCreateTime(TimeUtil.currentTimeMillis());
            ackPacket.setPacketId(MessageContext.idGenerator().generateId());
            ackPacket.setMessageType(MessageTypeEnum.QOS_S2C_ACK.getType());
            MessageHelper.asyncSendMessage(ackPacket, Target.newBuilder().targetIdentity(from).deviceType(MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType())).targetServerAddress(MessageServerContext.serverProperties().getLocalServerAddress()).build());
        }
    }
}
