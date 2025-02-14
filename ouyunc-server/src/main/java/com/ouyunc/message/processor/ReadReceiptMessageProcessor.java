package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 已读回执消息处理器
 */
public class ReadReceiptMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptMessageProcessor.class);

    @Override
    public Type<? extends Byte> type() {
        return null;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("ReadReceiptMessageProcessor 正在处理已读回执消息...");
    }
}
