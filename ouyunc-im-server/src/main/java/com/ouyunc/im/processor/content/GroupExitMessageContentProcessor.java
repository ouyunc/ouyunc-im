package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 退群
 */
public class GroupExitMessageContentProcessor extends AbstractMessageContentProcessor{
    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_EXIT;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
