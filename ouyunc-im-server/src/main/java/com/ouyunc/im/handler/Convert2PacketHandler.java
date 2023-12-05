package com.ouyunc.im.handler;

import com.alibaba.fastjson2.JSON;
import com.google.gson.Gson;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.helper.MqttHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.LoginContent;
import com.ouyunc.im.utils.ReaderWriterUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.AttributeKey;
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

    private static Gson gson = new Gson();


    /**
     * @param ctx
     * @param msg
     * @return void
     * @Author fangzhenxun
     * @Description 类型转换
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
            packet = MqttHelper.wrapMqtt2Packet(ctx, (MqttMessage) msg);
        }
        if (packet != null) {
            MDC.put(IMConstant.LOG_TRACE_ID, String.valueOf(packet.getPacketId()));
            log.info("消息包转换为：{}", packet);
            setAppKey(ctx, msg, packet);
            // 直接传递
            ctx.fireChannelRead(packet);
        } else {
            throw new IMException("协议转换为packet发生异常,暂不支持该协议！");
        }
    }


    /**
     * @param ctx
     * @param msg
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 给消息包设置appKey, 提供下游使用
     */
    private void setAppKey(ChannelHandlerContext ctx, Object msg, Packet packet) {
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = null;
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
            innerExtraData = new InnerExtraData();
        }
        if (extraMessage != null) {
            innerExtraData = extraMessage.getInnerExtraData();
            if (innerExtraData == null) {
                innerExtraData = new InnerExtraData();
            }
        }
        // 判断是否是首次在集群间传递消息
        if (!innerExtraData.isDelivery()) {
            // 首次进行传递时，将目标以及目标主机和所登录的设备进行设置
            if (MessageEnum.IM_LOGIN.getValue() == packet.getMessageType()) {
                // 这里不同的消息会有不同的设置方式
                String appKey = null;
                if (msg instanceof MqttMessage) {
                    // @todo 这里先写死
                    appKey = "ouyunc";
                }
                if (msg instanceof BinaryWebSocketFrame) {
                    LoginContent loginContent = JSON.parseObject(message.getContent(), LoginContent.class);
                    appKey = loginContent.getAppKey();
                }
                innerExtraData.setAppKey(appKey);
            }
            if (MessageEnum.IM_LOGIN.getValue() != packet.getMessageType()) {
                AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
                LoginUserInfo loginUserInfo = ctx.channel().attr(channelTagLoginKey).get();
                innerExtraData.setAppKey(loginUserInfo.getAppKey());
            }
            extraMessage.setOutExtraData(message.getExtra());
            extraMessage.setInnerExtraData(innerExtraData);
            message.setExtra(JSON.toJSONString(extraMessage));
        }

    }

}
