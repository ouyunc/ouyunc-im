package com.ouyunc.im.handler;

import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息监控处理器（可以作为鉴黄，敏感政治言论，非法言论等逻辑处理，也可以接入第三方去处理）
 * @Version V1.0
 **/
public class MonitorHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(MonitorHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 消息监控处理逻辑 @todo 目前这里不做处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("监控处理器MonitorHandler正在监控消息：{}", packet);
        ctx.fireChannelRead(packet);
    }
}
