package com.ouyu.im.processor;

import com.ouyu.cache.l1.distributed.redis.lettuce.RedisFactory;
import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.enums.BroadcastTypeEnum;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author fangzhenxun
 * @Description: 广播消息类处理
 * @Version V1.0
 **/
public class BroadcastMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(BroadcastMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_BROADCAST;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 如果是消息的投递，则直接放行
        final Message pChatMessage = (Message) packet.getMessage();
        // 如果不可达服务列表不为空，或者当前重试次数不等于0
        if (pChatMessage.isDelivery()){
            // 集群内部处理消息，由于在第一次传递的时候已经校验是否登录了这里不再进行二次校验
            ctx.fireChannelRead(packet);
            return;
        }
        UserHelper.doAuthentication(pChatMessage.getFrom(), ctx, packet);
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        List<String> tos = new ArrayList<>();
        // 广播给谁
        final Message broadcastMessage = (Message) packet.getMessage();
        String from = broadcastMessage.getFrom();
        String broadcastType = broadcastMessage.getContent();
        BroadcastTypeEnum broadcastTypeEnum = BroadcastTypeEnum.getBroadcastTypeEnumByType(broadcastType);
        if (BroadcastTypeEnum.ONLINE.equals(broadcastTypeEnum) || BroadcastTypeEnum.OFFLINE.equals(broadcastTypeEnum)) {
            Map<String, String> friendPhoneMap = RedisFactory.redisTemplate().opsForHash().entries(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.CONTACT_CACHE_PREFIX + from);
            tos = friendPhoneMap.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());
        }
        broadcast(ctx, packet, tos);
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
