package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.handler.EphemeralRemoteClientRealIpHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * HAProxyProtocol 代理协议，为了获取真实客户端ip， 参考 HAProxyMessageDecoder实现
 */
public class HAProxyProtocolDispatcherBiProcessor implements ProtocolDispatcherBiProcessor {
    private static final Logger log = LoggerFactory.getLogger(HAProxyProtocolDispatcherBiProcessor.class);


    private int version = -1;

    /**
     * V2 protocol binary header prefix
     */
    private static final byte[] BINARY_PREFIX = {
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x00,
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x51,
            (byte) 0x55,
            (byte) 0x49,
            (byte) 0x54,
            (byte) 0x0A
    };

    /**
     * V1 protocol binary header prefix
     */
    private static final byte[] TEXT_PREFIX = {
            (byte) 'P',
            (byte) 'R',
            (byte) 'O',
            (byte) 'X',
            (byte) 'Y',
    };
    @Override
    public boolean match(ByteBuf in) {
        final int n = in.readableBytes();
        // per spec, the version number is found in the 13th byte
        if (n < 13) {
            return false;
        }
        // 继续校验是否满足V1/V2版本
        boolean v1Flag = true;
        boolean v2Flag = true;
        for (int i = 0; i < 12; i++) {
            final byte b = in.getByte(i);
            // 判断是否符合V1
            if (v1Flag && i < TEXT_PREFIX.length && b != TEXT_PREFIX[i]) {
                v1Flag = false;
            }
            // 提前判断是否是V1
            if (i == TEXT_PREFIX.length - 1 && v1Flag) {
                // 满足V1 的版本
                this.version = 1;
                return true;
            }
            // 判断是否符合V2
            if (v2Flag && b != BINARY_PREFIX[i]) {
                v2Flag = false;
            }
            // 提前判断是否是V1/V2
            if (!v2Flag && !v1Flag) {
                return false;
            }
        }
        // 如果没有提前返回则是V2
        this.version = 2;
        // 解析V2 内容
        return true;
    }

    /**
     * 解完 PROXY 头后摘掉当前分发器，把应用层剩余字节再交给新的协议探测。
     * 不能让 HAProxyMessageDecoder 在 finished 后 skipBytes 吃掉 WS 首包。
     */
    @Override
    public void process(ChannelHandlerContext ctx, ByteBuf in) {
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.addLast(MessageConstant.HA_PROXY_PROTOCOL_DECODER_HANDLER, new HAProxyMessageDecoder());
        pipeline.addLast(MessageConstant.REMOTE_CLIENT_REAL_IP_HANDLER, new EphemeralRemoteClientRealIpHandler());
        pipeline.remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        pipeline.addLast(MessageConstant.PROTOCOL_DISPATCHER_HANDLER, new ProtocolDispatcher());
        ctx.fireChannelRead(in.retain());
    }

}
