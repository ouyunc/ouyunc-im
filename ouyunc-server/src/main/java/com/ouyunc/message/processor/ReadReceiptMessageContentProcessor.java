package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 已读回执消息处理器
 */
public class ReadReceiptMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.READ_RECEIPT_CONTENT;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("ReadReceiptMessageProcessor 正在处理已读回执消息...");
    }
}
