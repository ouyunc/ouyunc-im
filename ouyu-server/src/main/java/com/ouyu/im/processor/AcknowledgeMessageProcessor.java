package com.ouyu.im.processor;

import cn.hutool.json.JSONUtil;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.AcknowledgeMessage;
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
public class AcknowledgeMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(AcknowledgeMessageProcessor.class);




    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        AcknowledgeMessage acknowledgeMessage = (AcknowledgeMessage) packet.getMessage();
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
        AcknowledgeMessage acknowledgeMessage = (AcknowledgeMessage) packet.getMessage();
        String to = acknowledgeMessage.getTo();

        // @todo 判断是否在线
        Object obj = IMContext.LOGIN_USER_INFO_CACHE.get(to);
        LoginUserInfo loginUserInfo = null;
        if (obj != null) {
            loginUserInfo = JSONUtil.toBean(JSONUtil.toJsonStr(obj), LoginUserInfo.class);
        }
        String targetServerAddress = loginUserInfo.getLoginServerAddress();
        acknowledgeMessage.setTargetServerAddress(targetServerAddress);
        // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
        if (!IMContext.SERVER_CONFIG.isClusterEnable() || IMContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
            // 直接处理，清掉该消息对应的定时任务
            ScheduledFuture scheduledFuture = IMContext.ACK_SCHEDULE_CACHE.get(Long.parseLong(acknowledgeMessage.getContent()));
            // 取消任务
            scheduledFuture.cancel(true);
            if (scheduledFuture.isCancelled()) {
                IMContext.ACK_SCHEDULE_CACHE.invalidate(scheduledFuture);
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
