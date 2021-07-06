package com.ouyu.im.processor;

import com.ouyu.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 群组消息处理器
 * @Version V1.0
 **/
public class GChatMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(GChatMessageProcessor.class);


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
