package com.ouyunc.im.handler;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.qos.Qos;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * qos 消息质量处理,注意目前qos质量保证，只针对私聊和群聊
 */
public class QosHandler extends SimpleChannelInboundHandler<Packet> implements Qos {
    private static Logger log = LoggerFactory.getLogger(QosHandler.class);


    /**
     * qos 前置处理，一般用来做消息的去重（客户端超时未收到服务端的ack回应，会重发消息，这里判断如果已经到达服务器并保存则取消重试，并伪造收到ack进行回应发消息者，从而取消定时重试 ）
     * 目前只做私聊和群聊的qos
     * @param ctx
     * @param packet
     * @return
     */
    @Override
    public boolean preHandle(ChannelHandlerContext ctx, Packet packet) {
        log.info("qos 消息可靠前置处理器 QosHandler 正在处理...");
        // 如果是消息重试，则判断对方消息箱中是否存在
        MessageEnum messageEnum = MessageEnum.prototype(packet.getMessageType());
        if (messageEnum == null) {
            log.error("消息 :{}  的内容类型 messageType 不能为空！", packet.getPacketId(), packet.getMessageType());
            return false;
        }
        // 判断是否是消息重试的消息类型
        if (MessageEnum.IM_QOS_RETRY.equals(messageEnum)) {
            Message message = (Message) packet.getMessage();
            if (IMServerContext.SERVER_CONFIG.isAcknowledgeModeEnable()) {
                Packet retryPacket = JSON.parseObject(message.getContent(), Packet.class);
                Message retryMessage = (Message) retryPacket.getMessage();
                String to = retryMessage.getTo();
                String from = retryMessage.getFrom();
                // 判断该重试消息是否被服务器端处理过，如果未处理，则直接变换packet交给下游处理，如果处理过则直接发送
                Packet existPacket = DbHelper.readFromTimeline(to, retryPacket.getPacketId());
                if (existPacket != null) {
                    MessageHelper.doQos(from, retryPacket);
                    // 不往下面传递处理了
                    return false;
                }
                // 将消息赋值,重新走逻辑
                packet = retryPacket;
            }
        }
        return true;
    }

    /**
     * qos 后置处理，如果代码走到这里，就证明消息已经在服务端正常处理，通知客户端服务端已经收到消息;
     * 需等待接收端的回应（注意：如果发送端有需要qos，则假定接收端（群组或个人）可以正常接收消息并处理，
     * 如果接收端超时为响应接收到消息，则发送端会重发消息,消息id是相同的，这个时候会走PreHandle 进行校验，
     * 是否已经在接收端的信箱中，如果已经存在则通知对方并返回一个收到的ack 给消息发送者）；
     * 采用这种方式处理qos将延迟到重试
     * @param ctx
     * @param packet
     */
    @Override
    public void postHandle(ChannelHandlerContext ctx, Packet packet) {
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        // 只在消息首次到达服务的地方发送ack给外部客户端
        if (IMServerContext.SERVER_CONFIG.isAcknowledgeModeEnable() && (extraMessage == null || extraMessage.getInnerExtraData() == null || !extraMessage.getInnerExtraData().isDelivery())) {
            // 判断接收端是否离线（消息是否存入离线数据库），如果离线则需要在发送收到的同时伪造已经存入离线消息；qos
            MessageHelper.doQos(message.getFrom(), packet);
        }
    }

    /**
     * qos 处理
     * @param ctx
     * @param packet
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 判断用哪个方法处理执行
        String handlerName = ctx.name();
        if (IMConstant.QOS_HANDLER_PRE.equals(handlerName) && !preHandle(ctx, packet)) {
            return;
        }
        if (IMConstant.QOS_HANDLER_POST.equals(handlerName)) {
            postHandle(ctx, packet);
        }
        ctx.fireChannelRead(packet);
    }
}
