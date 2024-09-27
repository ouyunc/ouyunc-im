package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.handler.MqttProtocolDispatcherHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description mqtt 协议
 */
public class MqttProtocolDispatcherProcessor implements ProtocolDispatcherProcessor {
    private static final Logger log = LoggerFactory.getLogger(MqttProtocolDispatcherProcessor.class);

    @Override
    public boolean match(ByteBuf in) {
        return isMqtt(in);
    }

    @Override
    public void process(ChannelHandlerContext ctx, ByteBuf in) {
        ctx.pipeline()
                .addLast(MessageConstant.MQTT_DISPATCHER_HANDLER, new MqttProtocolDispatcherHandler())
                .remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }
    /**
     * @Author fzx
     * @Description 判断是否是mqtt的connect协议
     */
    private boolean isMqtt(ByteBuf in) {
        for (int i = 0; i < 4; i++) {
            final byte magic2 = in.getByte(i + 1);
            // 可变长度所占用字节长度为1个字节
            if (magic2 >= 0) {
                return determineMqtt(in, i);
            }
        }
        return false;
    }

    /**
     * @Author fzx
     * @Description 选取具体哪个mqtt 以及协议版本
     */
    private boolean determineMqtt(ByteBuf in, int retainLength) {
        final byte MSB = in.getByte(retainLength + 2);
        final byte LSB = in.getByte(retainLength + 3);
        final byte M = in.getByte(retainLength + 4);
        final byte Q = in.getByte(retainLength + 5);
        final byte T_I = in.getByte(retainLength + 6);
        final byte T_s = in.getByte(retainLength + 7);
        // 判断是哪个协议版本,协议可能是3.1.1 / 5.0
        if (MSB == 0 && LSB == 4) {
            if (M == 'M' && Q == 'Q' && T_I == 'T' && T_s == 'T') {
                // 此时是mqtt 协议,且协议版本号为protocolVersion : 4 => 3.1.1, 5 => 5.0
                final byte protocolVersion = in.getByte(retainLength + 8);
                return protocolVersion == 4 || protocolVersion == 5;
            }
        } else if (MSB == 0 && LSB == 6) {
            final byte d = in.getByte(retainLength + 8);
            final byte p = in.getByte(retainLength + 9);
            //协议可能是3.1
            if (M == 'M' && Q == 'Q' && T_I == 'I' && T_s == 's' && d == 'd' && p == 'p') {
                // 此时是mqtt 协议,且协议版本号为protocolVersion : 3 => 3.1
                final byte protocolVersion = in.getByte(retainLength + 10);
                return protocolVersion == 3;
            }
        }
        return false;
    }

}
