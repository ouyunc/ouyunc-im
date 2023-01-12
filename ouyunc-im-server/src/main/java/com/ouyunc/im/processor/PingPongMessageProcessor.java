package com.ouyunc.im.processor;

import cn.hutool.core.date.SystemClock;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 外部客户端心跳消息
 * @Version V3.0
 **/
public class PingPongMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(PingPongMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_PING_PONG;
    }


    /**
     * 处理外部客户端 ping-pong 消息
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("PingPongMessageProcessor 正在处理心跳 {} ...", packet);
        }
        // 处理心跳消息
        Message heartBeatMessage = (Message) packet.getMessage();
        String from = heartBeatMessage.getFrom();
        byte loginDeviceType = packet.getDeviceType();
        final String comboIdentity = IdentityUtil.generalComboIdentity(from, loginDeviceType);
        if (MessageContentEnum.PING_CONTENT.type() == heartBeatMessage.getContentType()) {
            // 可能在三次之内再次发起心跳，此时需要清除 之前心跳超时次数的历史记录
            AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_READ_TIMEOUT);
            ctx.channel().attr(channelTagReadTimeoutKey).set(null);
            // 发送pong
            heartBeatMessage.setFrom(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
            heartBeatMessage.setTo(from);
            heartBeatMessage.setContentType(MessageContentEnum.PONG_CONTENT.type());
            heartBeatMessage.setCreateTime(SystemClock.now());
            packet.setPacketId(SnowflakeUtil.nextId());
            packet.setIp(IMServerContext.SERVER_CONFIG.getLocalHost());
            // 写回的是websocket还是其他类型的数据
            MessageHelper.sendMessage(packet, comboIdentity);
        }else {
            // 非法心跳类型,解绑用户
            log.error("非法心跳内容类型: {},正在解绑用户channel: {}", heartBeatMessage.getContentType(), ctx.channel().id().asShortText());
            UserHelper.unbind(from, loginDeviceType, ctx);
        }
    }
}
