package com.ouyu.im.processor;

import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 好友 同意添加 处理器
 * @Version V1.0
 **/
public class FriendAddResolveMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(FriendAddResolveMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_FRIEND_ADD_RESOLVE;
    }

    /**
     * @Author fangzhenxun
     * @Description 登录后做消息认证
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 如果是消息的投递，则直接放行
        Message friendMessage = (Message) packet.getMessage();
        if (friendMessage.isDelivery()){
            // 集群内部处理消息，由于在第一次传递的时候已经校验是否登录了这里不再进行二次校验
            ctx.fireChannelRead(packet);
            return;
        }
        UserHelper.doAuthentication(friendMessage.getFrom(), ctx, packet);
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        process(ctx, packet, (ctx0, packet0)->{

        });

    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
