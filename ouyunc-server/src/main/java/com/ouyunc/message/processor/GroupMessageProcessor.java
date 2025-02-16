package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 群聊消息处理器
 */
public class GroupMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(GroupMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneMessageProcessor 正在处理一对一消息...");
        AbstractBaseProcessor<? extends Number> contentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (contentProcessor != null) {
            contentProcessor.process(ctx, packet);
            return;
        }
        // 走默认内容处理逻辑
        // do something

    }
}
