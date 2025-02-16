package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 撤销已读回执消息处理器，针对已读的消息可以进行撤销，变成未读
 */
public class WithdrawReadReceiptMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(WithdrawReadReceiptMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.WITHDRAW_READ_RECEIPT_CONTENT;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
    }
}
