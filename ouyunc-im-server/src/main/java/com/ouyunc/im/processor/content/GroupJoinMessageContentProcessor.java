package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 加群
 */
public class GroupJoinMessageContentProcessor extends AbstractMessageContentProcessor{
    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_JOIN;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
