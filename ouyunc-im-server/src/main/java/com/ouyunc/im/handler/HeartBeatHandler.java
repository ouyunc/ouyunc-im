package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.utils.IdentityUtil;
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
 * @Version V3.0
 **/
public class HeartBeatHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(HeartBeatHandler.class);


    /**
     * @Author fangzhenxun
     * @Description 这里处理业务逻辑,如果开启外部客户端的心跳，则所有业务消息都会走这里，这里根据规则放行或拦截
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 由于所有消息都会经过心跳处理器，所以这里对真正需要心跳处理的数据进行拦截处理，其他的数据直接放行不做处理
        // 需要判断是否是心跳的消息类型
        if (MessageEnum.IM_PING_PONG.getValue() != packet.getMessageType()) {
            // 交给下面业务处理器去处理
            ctx.fireChannelRead(packet);
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("HeartBeatHandler 正在处理心跳...");
        }
        // 如果是外部客户端的心跳消息则直接掉用心跳消息处理器来进行处理,然后就结束了，不会往下面透传消息
        IMProcessContext.MESSAGE_PROCESSOR.get(MessageEnum.IM_PING_PONG.getValue()).doProcess(ctx, packet);
    }

    /**
     * @Author fangzhenxun
     * @Description 读事件触发后会走这里（服务端读取客户端信息超时，客户端超过一段时间没有发送心跳消息会触发这里，当然也可以在客户端做心跳的检测梳理）
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
                // @todo 在这里面写在线状态的逻辑，偶然事件需要排除,重试（一定的策略）
                IdleStateEvent idleStateEvent = (IdleStateEvent)event;
                if (IdleState.READER_IDLE.equals(idleStateEvent.state())) {
                    // 记录该channel 是第几次连续触发读超时，如果超过三次，则标注该客户端离线，并尝试通知客户端进行重试连接
                    AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_READ_TIMEOUT);
                    // channel 连续读超时次数
                    Integer readTimeoutTimes = channel.attr(channelTagReadTimeoutKey).get();
                    if (readTimeoutTimes == null) {
                        readTimeoutTimes = 1;
                    }
                    log.info("外部客户端channel: {} 的 idle: {} 第 {} 触发了",channel.id().asShortText(), ((IdleStateEvent)event).state(), readTimeoutTimes);

                    // 如果连续超过三次
                    if (readTimeoutTimes > IMServerContext.SERVER_CONFIG.getHeartBeatWaitRetry()-1) {
                        // 多次没有收到心跳断开连接
                        AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
                        final LoginUserInfo loginUserInfo = channel.attr(channelTagLoginKey).get();
                        // 这里的ctx 与注册表中的ctx 是同一个应用,
                        UserHelper.unbind(IdentityUtil.generalComboIdentity(loginUserInfo.getIdentity(), loginUserInfo.getDeviceEnum().getName()), ctx);
                        return;
                    }
                    // 设置连续超时次数
                    channel.attr(channelTagReadTimeoutKey).set(++readTimeoutTimes);
                }
            }else {
                log.error("当前channel->id: {} inActive", channel.id().asShortText());
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }


}
