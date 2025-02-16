package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 一对一（单聊）消息处理器
 */
public class One2OneMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneMessageProcessor 正在处理一对一消息...");
        AbstractBaseProcessor<? extends Number> contentProcessor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (contentProcessor != null) {
            contentProcessor.process(ctx, packet);
            return;
        }
        // 发送消息到对方
        Message message = packet.getMessage();
        // 保存消息
        if (!repository().saveMessage(packet, MessageServerContext.serverProperties().isQosEnable())) {
            log.error("一对一消息: {} 保存消息异常", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(new MessageException("一对一保存消息异常!"), packet), true);
            return;
        }
        // 保存到磁盘
        //MessageServerContext.publishEvent(new SaveMessageEvent(packet), true);
        // 获取对方在线的所有客户端
        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());
        // 如果不在线的话，先保存到离线消息队列中，然后发送消息到对方
        if (CollectionUtils.isEmpty(toLoginClientInfos)) {
            log.warn("发送消息到: {} 失败, 对方不在线!,消息已存储到离线队列中", message.getTo());
            return;
        }
        // 异步发送消息
        MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
    }
}
