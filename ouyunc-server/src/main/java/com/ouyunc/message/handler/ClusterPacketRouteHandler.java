package com.ouyunc.message.handler;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群中如果对方客户端不在同一台server中需要将消息路由投递到登录的服务中，为了不在业务处理器中写重复的判断是否是集群传递过来的消息，在这里统一进行处理
 */
public class ClusterPacketRouteHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(ClusterPacketRouteHandler.class);


    /**
     * 在集群环境下收发消息的客户端不在同一个服务中，需要进行路由处理则使用该方式进行路由处理
     * @param ctx
     * @param packet
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("集群路由处理器PacketClusterRouterHandler正在处理packet= {} ...", packet);
        Metadata metadata = packet.getMessage().getMetadata();
        // 判断是否从其他服务路由过来的消息
        if (metadata != null && metadata.isRouted()) {
            log.info("当前服务：{} 接收到集群的消息: {}", MessageContext.messageProperties.getLocalServerAddress(), Serializer.JSON.serializeToString(packet));
            MessageHelper.asyncSendMessage(packet, metadata.getTarget());
            return;
        }
        // 交给下个处理器, 如果上面条件没满足，则直接交给下个处理器去处理，一般是syn-ack集群内部心跳才会走这里
        ctx.fireChannelRead(packet);
    }
}
