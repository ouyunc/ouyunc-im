package com.ouyu.im.processor;

import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.MessageContentEnum;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.utils.TimeUtil;
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
    public MessageEnum messageType() {
        return MessageEnum.IM_PING_PONG;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {

    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // 处理心跳消息
        Message heartBeatMessage = (Message) packet.getMessage();
        final String identity = heartBeatMessage.getFrom();
        if (ImConstant.PING.equals(heartBeatMessage.getContent())) {
            // 可能在三次之内再次发起心跳，此时需要清除次数历史记录
            AttributeKey<Integer> channelTagReadTimeoutKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_READ_TIMEOUT);
            ctx.channel().attr(channelTagReadTimeoutKey).set(null);

            // 发送pong
            heartBeatMessage.setFrom(IMServerContext.LOCAL_ADDRESS);
            heartBeatMessage.setTo(identity);
            heartBeatMessage.setContentType(MessageContentEnum.TEXT_CONTENT.code());
            heartBeatMessage.setContent(ImConstant.PONG);
            heartBeatMessage.setCreateTime(TimeUtil.currentTimestamp());
            // 写回的是websocket还是其他类型的数据
            MessageHelper.sendMessage(packet, identity);
        }else {
            // 非法心跳类型,移除并关闭
            IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + identity);
            IMServerContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
            ctx.close();
        }
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
