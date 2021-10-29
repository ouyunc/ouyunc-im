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
 * @Description: 私聊消息处理器
 * @Version V1.0
 **/
public class PChatMessageProcessor  extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(PChatMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_P_CHAT;
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

        // 全量消息也涉及到读扩散，写扩散
        // @todo 本次版本只保证单机可靠，如果该服务宕机则该消息可能会丢失，集群可靠，以及集群消息是否实现心跳则在下个版本完成
        // 当该服务宕机/下线时时需要根据一定策略将分布式缓存中的ip对应的等待ack的消息按照策略拉倒某个服务上
        // 经过各种拦截策略的判断终于到达消息的最终逻辑处理了
        // 1, 所有消息入库(cache/db)；这里如何设置以及存储的选型是非常重要的
        // 需要判断目的地是否是本服务器， 如果不是本服务，判断是否是集群
        //ws 的私聊处理，私聊就是一对一，最终都要转成websocket帧写出
        //1, 先判断消息的序列化方式
        process(ctx, packet, (ctx0, packet0)->{
            // 做自己的逻辑处理

        });
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
