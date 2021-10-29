package com.ouyu.im.processor;

import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 应答消息处理器
 * @Version V1.0
 **/
public class AckMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(AckMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_ACK;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        Message acknowledgeMessage = (Message) packet.getMessage();
        // 如果不可达服务列表不为空，或者当前重试次数不等于0
        if (acknowledgeMessage.isDelivery()){
            // 集群内部处理消息，由于在第一次传递的时候已经校验是否登录了这里不再进行二次校验
            ctx.fireChannelRead(packet);
            return;
        }
        UserHelper.doAuthentication(acknowledgeMessage.getFrom(), ctx, packet);
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        Message ackMessage = (Message) packet.getMessage();
        // 这里需要前端传递两个值一个消息id一个消息目标的服务器地址
        String targetServerAddress = ackMessage.getToServerAddress();
        // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
        if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
            // 直接处理，清掉该消息对应的定时任务
            ScheduledFuture scheduledFuture = IMServerContext.ACK_SCHEDULE_CACHE.get(Long.parseLong(ackMessage.getContent()));
            // 取消任务
            scheduledFuture.cancel(true);
            if (scheduledFuture.isCancelled()) {
                IMServerContext.ACK_SCHEDULE_CACHE.invalidate(scheduledFuture);
            }
            return;
        }

        // 通过集群漫游到目标服务器上
        MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        System.out.println("ack");
        log.info("ack");
    }
}
