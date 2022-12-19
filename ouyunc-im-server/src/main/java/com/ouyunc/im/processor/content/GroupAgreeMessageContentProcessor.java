package com.ouyunc.im.processor.content;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 同意加群
 */
public class GroupAgreeMessageContentProcessor extends AbstractMessageContentProcessor{
    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_AGREE;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
