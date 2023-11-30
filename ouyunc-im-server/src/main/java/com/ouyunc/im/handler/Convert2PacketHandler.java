package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.content.MqttContent;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.serialize.Serializer;
import com.ouyunc.im.utils.ReaderWriterUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @Author fangzhenxun
 * @Description: 将非packet 协议类型转成Packet,服务内部只处理packet
 * @Version V3.0
 **/
public class Convert2PacketHandler extends SimpleChannelInboundHandler<Object> {
    private static Logger log = LoggerFactory.getLogger(Convert2PacketHandler.class);



    /**
     * @Author fangzhenxun
     * @Description 类型转换
     * @param ctx
     * @param msg
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Packet packet = null;
        if (msg instanceof BinaryWebSocketFrame) {
            packet = ReaderWriterUtil.readByteBuf2Packet(((BinaryWebSocketFrame) msg).content());
        }
        if (msg instanceof Packet) {
            packet = (Packet) msg;
        }
        // 处理mqtt消息
        if (msg instanceof MqttMessage) {
            MqttMessage mqttMessage = (MqttMessage)msg;
            // 解码成功后，再去转换传递
            if (mqttMessage.decoderResult().isSuccess()) {
                packet = ReaderWriterUtil.convertOther2Packet(mqttMessage, mqttMessage0 ->{
                    MqttContent mqttContent = new MqttContent(mqttMessage0.fixedHeader(), mqttMessage0.variableHeader(), mqttMessage0.payload());
                    return new Packet(Protocol.MQTT.getProtocol(), Protocol.MQTT.getVersion(), SnowflakeUtil.nextId(), DeviceEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getIp(), MessageEnum.MQTT.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(),  mqttContent);
                });
            }
        }
        if (packet != null) {
            MDC.put(IMConstant.LOG_TRACE_ID, String.valueOf(packet.getPacketId()));
            log.info("消息包转换为：{}", packet);
            ctx.fireChannelRead(packet);
        }else {
            throw new IMException("协议转换为packet发生异常,暂不支持该协议！");
        }
    }
}
