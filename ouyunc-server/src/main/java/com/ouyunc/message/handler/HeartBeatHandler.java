package com.ouyunc.message.handler;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.WsMessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 外部客户端的心跳
 **/
public class HeartBeatHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(HeartBeatHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 这里处理业务逻辑, 如果开启外部客户端的心跳，则所有业务消息都会走这里，这里根据规则放行或拦截
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 由于所有消息都会经过心跳处理器，所以这里对真正需要心跳处理的数据进行拦截处理，其他的数据直接放行不做处理
        // 需要判断是否是心跳的消息类型
        if (WsMessageTypeEnum.PING_PONG.getType() != packet.getMessageType()) {
            // 交给下面业务处理器去处理
            ctx.fireChannelRead(packet);
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("HeartBeatHandler 正在处理客户端心跳...");
        }
        // 如果是外部客户端的心跳消息则直接掉用心跳消息处理器来进行处理,然后就结束了，不会往下面透传消息
        MessageServerContext.messageProcessorCache.get(WsMessageTypeEnum.PING_PONG.getType()).process(ctx, packet);
    }

    /**
     * @param ctx
     * @param event
     * @return void
     * @Author fzx
     * @Description 读事件触发后会走这里（服务端读取客户端信息超时，客户端超过一段时间没有发送心跳消息会触发这里，当然也可以在客户端做心跳的检测梳理）
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        Channel channel = ctx.channel();
        // 当该空闲事件触发时，则说明该通道channel没有任何的消息过来,则需要进行判断进行释放处理
        if (event instanceof IdleStateEvent) {
            // 判断该通道是否是存活
            if (channel.isActive()) {
                IdleStateEvent idleStateEvent = (IdleStateEvent) event;
                // 一定时间没有收到外部客户端发来的消息，出触发这里
                if (IdleState.READER_IDLE.equals(idleStateEvent.state())) {
                    // 记录该channel 是第几次连续触发读超时，如果超过三次，则标注该客户端离线，并尝试通知客户端进行重试连接
                    // channel 连续读超时次数
                    Integer readTimeoutTimes = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES);
                    if (readTimeoutTimes == null) {
                        readTimeoutTimes = MessageConstant.ONE;
                    }
                    log.info("外部客户端channel: {} 的 read_idle: {} 第 {} 触发了", channel.id().asShortText(), ((IdleStateEvent) event).state(), readTimeoutTimes);
                    // 如果连续超过三次
                    if (readTimeoutTimes > MessageServerContext.serverProperties().getClientHeartBeatWaitRetry() - MessageConstant.ONE) {
                        ctx.close();
                        return;
                    }
                    // 设置连续超时次数
                    ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES, ++readTimeoutTimes);
                }
            } else {
                log.error("当前channel->id: {} inActive", channel.id().asShortText());
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }


}
