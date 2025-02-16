package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 撤销消息处理器
 */
public class WithdrawMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.WITHDRAW_CONTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("WithdrawMessageProcessor 正在处理撤销消息...");
        Message message = packet.getMessage();
        // 获取需要撤销的消息id，（这里使用String类型接收）
        List<String> packetIds = JSON.parseArray(message.getContent(), String.class);
        if (CollectionUtils.isEmpty(packetIds)) {
            log.warn("WithdrawMessageProcessor 处理撤销消息异常，packetIds: {} 为空", packetIds);
            return;
        }
        // 撤销消息
        if (!repository().withdrawMessage(packetIds)) {
            // 未撤销成功
            log.error("WithdrawMessageProcessor 处理撤销消息失败，packetIds: {}", packetIds);
            return;
        }
        log.info("WithdrawMessageProcessor 处理撤销消息成功，packetIds: {}", packetIds);

    }
}
