package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加群申请，管理员同意，管理员拒绝，踢出群/退群/解散群/禁言（全部禁言/部分禁言）/黑名单
 */
public class GroupRequestMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(GroupRequestMessageProcessor.class);

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.IM_GROUP_REQUEST;
    }

    /**
     * 群相关请求
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupRequestMessageProcessor 正在处理好友请求消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0) -> {
            // 判断是什么类型的消息，好友申请，好友拒绝，好友同意等
            IMProcessContext.MESSAGE_CONTENT_PROCESSOR.get(((Message) packet.getMessage()).getContentType()).doProcess(ctx, packet);
        });

    }
}
