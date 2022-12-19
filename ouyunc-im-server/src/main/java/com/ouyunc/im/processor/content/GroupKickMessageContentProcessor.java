package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 踢出群群
 */
public class GroupKickMessageContentProcessor extends AbstractMessageContentProcessor{
    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_KICK;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
