package com.ouyu.im.processor;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.HeartBeatMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 外部客户端心跳消息
 * @Version V1.0
 **/
public class PingPongMessageProcessor  extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(PingPongMessageProcessor.class);



    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // 处理心跳消息
        HeartBeatMessage heartBeatMessage = (HeartBeatMessage) packet.getMessage();
        final String identity = heartBeatMessage.getFrom();
        if (ImConstant.PING.equals(heartBeatMessage.getContent())) {
            // 可能在三次之内再次发起心跳，此时需要清除次数历史记录
            AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_READ_TIMEOUT);
            ctx.channel().attr(channelTagReadTimeoutKey).set(null);

            // 发送pong
            heartBeatMessage.setContent(ImConstant.PONG);
            heartBeatMessage.setFrom(IMContext.LOCAL_ADDRESS);
            heartBeatMessage.setTo(identity);
            // 写回的是websocket还是其他类型的数据
            MessageHelper.sendMessage(packet, identity);
        }else {
            // 非法心跳类型,移除并关闭
            IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
            IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
            ctx.close();
        }
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
