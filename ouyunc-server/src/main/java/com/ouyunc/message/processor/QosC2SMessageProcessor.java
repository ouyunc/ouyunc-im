package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.RemoveOfflineEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: qos外部客户端已经收到消息,只有开启qos 且在服务端模式下才会处理相关逻辑
 **/
public final class QosC2SMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(QosC2SMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.QOS_C2S_ACK;
    }

    /**
     * 外部客户端接收到消息后，发送消息已接收给服务端，做消息已接收确认
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (MessageServerContext.serverProperties().isQosEnable() && QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())) {
            Message message = packet.getMessage();
            String receivedPackageId = message.getContent();
            log.info("QosC2SMessageProcessor 外部客户端接收到消息id: {}", receivedPackageId);
            // 移除离线消息,通过异步发送移除离线消息事件
            MessageServerContext.publishEvent(new RemoveOfflineEvent(packet), true);
            // 停止本地的qos定时任务
            ScheduleTimer.cancel(receivedPackageId);
        }else {
            log.warn("QosC2SMessageProcessor qos未开启或者qos模式不是服务端模式,忽略处理");
        }

    }
}
