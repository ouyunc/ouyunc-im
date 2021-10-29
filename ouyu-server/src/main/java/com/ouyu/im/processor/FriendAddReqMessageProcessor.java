package com.ouyu.im.processor;

import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.entity.NewFriendPacket;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 好友 添加/删除/拒绝/同意 处理器
 * @Version V1.0
 **/
public class FriendAddReqMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(FriendAddReqMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_FRIEND_ADD_REQ;
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
            // 将添加好友消息放到好友添加中
            Message message = (Message) packet0.getMessage();
            // 总共存储三种类型的数据，一个
            // 只能一方发送申请好友，暂时不能同时申请对方添加好友
            // 判断是否存在对方的申请
            Object obj = IMServerContext.FRIEND_STATUS_CACHE.opsForHash().get(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.NEW_FRIEND_CACHE_PREFIX + CacheConstant.TO_CACHE_PREFIX + message.getFrom(), message.getTo());
            if (obj == null) {
                IMServerContext.FRIEND_STATUS_CACHE.opsForHash().putIfAbsent(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.NEW_FRIEND_CACHE_PREFIX + CacheConstant.FROM_CACHE_PREFIX  + message.getFrom() , message.getTo(), new NewFriendPacket(1, packet));
                IMServerContext.FRIEND_STATUS_CACHE.opsForHash().putIfAbsent(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.NEW_FRIEND_CACHE_PREFIX + CacheConstant.TO_CACHE_PREFIX  + message.getTo() , message.getFrom(), new NewFriendPacket(0, packet));
            }
            IMServerContext.FRIEND_STATUS_CACHE.opsForHash().put(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.NEW_FRIEND_CACHE_PREFIX + CacheConstant.IDENTITY_CACHE_PREFIX  + message.getTo() , String.valueOf(packet.getPacketId()), packet);
        });

    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
