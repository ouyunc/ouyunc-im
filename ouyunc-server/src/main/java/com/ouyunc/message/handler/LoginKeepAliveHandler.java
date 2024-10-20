package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.constant.enums.WsMessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录保活处理器
 */
public class LoginKeepAliveHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(LoginKeepAliveHandler.class);

    /**
     * 客户端登录保活, 生产者
     * @param ctx
     * @param packet
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("客户端登录保活...");
        // 每次有消息数据进来, 从ctx上次心跳时间，从登录信息中解析心跳间隔时间
        // 获取该channel 上次的心跳时间戳
        if (!MessageServerContext.serverProperties().isClientHeartBeatEnable() || !SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
            ctx.fireChannelRead(packet);
            return;
        }
        // 判断是否是心跳消息
        if (WsMessageTypeEnum.PING_PONG.getType() == packet.getMessageType()) {
            // 设置本次时间为
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP, packet.getMessage().getMetadata().getServerTime());
            //  将放入保活队列
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            MessageServerContext.clientKeepAliveQueue.offer(loginClientInfo);
            ctx.fireChannelRead(packet);
            return;
        }
        // 如果不是心跳，继续判断该消息是否到达心跳的时间
        Long lastHeartbeatTimestamp = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP);
        if (lastHeartbeatTimestamp == null) {
            ctx.fireChannelRead(packet);
            return;
        }
        // 获取channel 心跳超时时间
        Integer heartbeatTimeout = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
        if (heartbeatTimeout == null) {
            ctx.fireChannelRead(packet);
            return;
        }
        // 获取消息到达服务器的时间戳
        long currentTimeMillis = packet.getMessage().getMetadata().getServerTime();
        // 判断当前时间是否大于等于上次心跳时间戳+心跳时间
        if (currentTimeMillis >= lastHeartbeatTimestamp + heartbeatTimeout) {
            // 满足条件重新设置上次心跳
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP, packet.getMessage().getMetadata().getServerTime());
            // 将放入保活队列
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            MessageServerContext.clientKeepAliveQueue.offer(loginClientInfo);
        }
        ctx.fireChannelRead(packet);
    }
}
