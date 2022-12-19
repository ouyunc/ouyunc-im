package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 拒绝加群
 */
public class GroupRefuseMessageContentProcessor extends AbstractMessageContentProcessor{
    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_REFUSE;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
