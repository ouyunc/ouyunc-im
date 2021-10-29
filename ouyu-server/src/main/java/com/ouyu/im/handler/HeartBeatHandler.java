package com.ouyu.im.handler;

import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.entity.ChannelUserInfo;
import com.ouyu.im.innerclient.handler.IMClientHeartBeatHandler;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.constant.enums.MessageEnum;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 外部客户端的心跳
 * @Version V1.0
 **/
public class HeartBeatHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(IMClientHeartBeatHandler.class);


    /**
     * @Author fangzhenxun
     * @Description 这里处理业务逻辑
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 由于所有消息都会经过心跳处理器，所以这里对真正需要心跳处理的数据进行拦截处理，其他的数据直接放行不做处理
        // 需要判断是否是心跳的消息类型
        if (MessageEnum.IM_PING_PONG.getValue() != packet.getMessageType()) {
            // 交给下面处理
            ctx.fireChannelRead(packet);
            return;
        }
        IMServerContext.MESSAGE_PROCESSOR_CACHE.get(MessageEnum.IM_PING_PONG.getValue()).doProcess(ctx, packet);
    }

    /**
     * @Author fangzhenxun
     * @Description 读事件触发后会走这里
     * @param ctx
     * @param event
     * @return void
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        Channel channel = ctx.channel();
        // 当该空闲事件触发时，则说明该通道channel没有任何的消息过来,则需要进行判断进行释放处理
        if (event instanceof IdleStateEvent) {
            // 判断该通道是否是存活
            if(channel.isActive()) {
                // @todo 在这里面写在线状态的逻辑，偶然事件需要排除重试（一定的策略）
                IdleStateEvent idleStateEvent = (IdleStateEvent)event;
                if (IdleState.READER_IDLE.equals(idleStateEvent.state())) {
                    // 记录该channel 是第几次连续触发读超时，如果超过三次，则标注该客户端离线，并尝试通知客户端进行重试连接
                    AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_READ_TIMEOUT);
                    // channel 连续读超时次数
                    Integer readTimeoutTimes = channel.attr(channelTagReadTimeoutKey).get();
                    if (readTimeoutTimes == null) {
                        readTimeoutTimes = 0;
                    }
                    // 如果连续超过三次
                    if (readTimeoutTimes >= 2) {
                        // 没有收到心跳断开连接
                        AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
                        final ChannelUserInfo channelUserInfo = channel.attr(channelTagLoginKey).get();
                        if (channelUserInfo != null) {
                            IMServerContext.LOCAL_USER_CHANNEL_CACHE.invalidate(channelUserInfo.getIdentity());
                        }
                        // 移除缓存中的数据
                        IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + channelUserInfo.getIdentity());
                        // 关闭channel
                        ctx.close();
                        return;
                    }
                    // 设置连续超时次数
                    channel.attr(channelTagReadTimeoutKey).set(++readTimeoutTimes);
                }
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }


}
