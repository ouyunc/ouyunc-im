package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 撤销消息处理器
 */
public class WithdrawMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageProcessor.class);

    @Override
    public Type<? extends Byte> type() {
        return MessageTypeEnum.WITHDRAW;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("WithdrawMessageProcessor 正在处理撤销消息...");
        Message message = packet.getMessage();

    }
}
