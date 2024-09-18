package com.ouyunc.message.processor;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.WsMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.WsMessageTypeEnum;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * @Author fzx
 * @Description: 外部客户端心跳消息
 **/
public class PingPongMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(PingPongMessageProcessor.class);

    @Override
    public MessageType type() {
        return WsMessageTypeEnum.PING_PONG;
    }



    /***
     * @author fzx
     * @description 核心业务逻辑处理
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("PingPongMessageProcessor 正在处理外部客户端心跳 {} ...", packet);
        }
        // 可能在三次之内再次发起心跳，此时需要清除 之前心跳超时次数的历史记录
        AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES);
        ctx.channel().attr(channelTagReadTimeoutKey).set(null);
        // 发送pong
        // 处理心跳消息
        Message heartBeatMessage = packet.getMessage();
        String from = heartBeatMessage.getFrom();
        heartBeatMessage.setFrom(MessageContext.messageProperties.getLocalServerAddress());
        heartBeatMessage.setTo(from);
        heartBeatMessage.setContentType(WsMessageContentTypeEnum.PING_CONTENT.getType());
        heartBeatMessage.setCreateTime(Clock.systemUTC().millis());
        packet.setPacketId(MessageContext.<Long>idGenerator().generateId());
        // 写回的是websocket还是其他类型的数据
        MessageHelper.asyncSendMessage(packet, Target.newBuilder().targetIdentity(from).deviceType(MessageServerContext.deviceTypeCache.get(packet.getDeviceType())).build());

    }
}
