package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.helper.MqttHelper;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 处理mqtt 客户端主动断开连接的消息
 **/
public class MqttDisConnectMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(MqttDisConnectMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.MQTT_DISCONNECT;
    }


    /**
     * @Author fangzhenxun
     * @Description 真正处理逻辑的地方
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("MqttDisConnectMessageProcessor 正在处理客户端主动断开连接信息...");
        // do nothing
    }
}
