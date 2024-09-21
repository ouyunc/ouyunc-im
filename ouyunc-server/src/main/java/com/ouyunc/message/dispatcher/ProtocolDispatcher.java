package com.ouyunc.message.dispatcher;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * @Author fzx
 * @Description: 协议派发器, 具体请看netty 源码例子
 **/
public class ProtocolDispatcher extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger(ProtocolDispatcher.class);


    /**
     * @Author fzx
     * @Description 编解码协议分发
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        log.info("ProtocolDispatcher 协议派发器正在处理...");
        // 首先判断缓冲区是否可读
        if (in == null || !in.isReadable()) {
            log.error("缓冲区不可读！");
            throw new MessageException("缓冲区不可读！");
        }
        // Will use the first six bytes to detect a protocol.
        if (in.readableBytes() < 6) {
            return;
        }
        // 读索引必须从头开始读的，这样才能保证是第一次读取
        int readerIndex = in.readerIndex();
        if (readerIndex != 0) {
            return;
        }
        ProtocolDispatcherProcessor protocolDispatcherProcessor = getProtocolDispatcherProcessor(in);
        // 包含http/https/ws/wss
        if (protocolDispatcherProcessor != null) {
            protocolDispatcherProcessor.process(ctx);
        } else {
            // 不知道的协议将关闭
            log.error("正在关闭非法协议！，channelID: {}", ctx.channel().id().asShortText());
            in.clear();
            ctx.close();
        }
    }

    /***
     * @author fzx
     * @description
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HAProxyMessage proxyMessage) {
            log.info("真实客户端的代理信息: HAProxyMessage: {}", proxyMessage);
            // 存入ctx 中，注意不能跨服务从ctx 获取该值，后面会解析处理存到packet中传递
            AttributeKey<HAProxyMessage> proxyMessageKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_HAPROXY_PROTOCOL);
            ctx.channel().attr(proxyMessageKey).set(proxyMessage);
        }else{
            super.channelRead(ctx, msg);
        }
    }

    /***
     * @author fzx
     * @description 获取协议处理器
     */
    public ProtocolDispatcherProcessor getProtocolDispatcherProcessor(ByteBuf in) {
        // 匹配并获取协议分发器
        for (ProtocolDispatcherProcessor protocolDispatcherProcessor : MessageServerContext.protocolDispatcherProcessors) {
            if (protocolDispatcherProcessor.match(in)) {
                return protocolDispatcherProcessor;
            }
        }
        return null;
    }
}
