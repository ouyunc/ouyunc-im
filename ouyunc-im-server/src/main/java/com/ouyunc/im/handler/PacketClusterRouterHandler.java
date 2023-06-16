package com.ouyunc.im.handler;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群中如果对方客户端不在同一台server中需要将消息路由投递到登录的服务中，为了不在业务处理器中写重复的判断是否是集群传递过来的消息，在这里统一进行处理
 */
public class PacketClusterRouterHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(PacketClusterRouterHandler.class);


    /**
     * 在集群环境下收发消息的客户端不在同一个服务中，需要进行路由处理则使用该方式进行路由处理
     * @param ctx
     * @param packet
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("集群路由处理器PacketClusterRouterHandler正在处理packet= {} ...", packet);
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        if (extraMessage != null) {
            InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
            // 判断是否从其他服务路由过来的额消息
            if (innerExtraData != null && innerExtraData.isDelivery()) {
                //这里上一个if 已经做了判断，该服务肯定开启了集群，否则不会走到这里的，所以这里就不判断了。  || !IMServerContext.SERVER_CONFIG.isClusterEnable()
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(innerExtraData.getTargetServerAddress())) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(message.getTo(), innerExtraData.getDeviceEnum().getName()));
                    return;
                }
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(innerExtraData.getTargetServerAddress()));
                return;
            }
        }
        // 交给下个处理器, 如果上面条件没满足，则直接交给下个处理器去处理
        ctx.fireChannelRead(packet);
    }
}
