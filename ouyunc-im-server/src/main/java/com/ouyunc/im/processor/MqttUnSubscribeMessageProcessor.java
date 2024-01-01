package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 处理mqtt 客户端解除订阅的消息
 **/
public class MqttUnSubscribeMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(MqttUnSubscribeMessageProcessor.class);


    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.MQTT_UNSUBSCRIBE;
    }


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 真正处理逻辑的地方
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("MqttUnSubscribeMessageProcessor 正在处理客户端解除订阅信息...");
        // do nothing
    }
}
