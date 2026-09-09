package com.ouyunc.message.handler;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 集群中如果对方客户端不在同一台 server 中需要将消息路由投递到登录的服务中。
 * <p>{@code routed=true} 时按 {@link Target#getTargetServerAddress()} 继续投递；该地址是最终落地机，中间节点不得改写。
 */
public class ClusterPacketRouteHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(ClusterPacketRouteHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || !metadata.isRouted()) {
            // 未路由包交给后续处理器（如集群 SYN-ACK）
            ctx.fireChannelRead(packet);
            return;
        }
        Target target = metadata.getTarget();
        if (target == null) {
            log.warn("集群路由包缺少 target, packetId={}", packet.getPacketId());
            return;
        }
        String localServerAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        if (metadata.isLocalBroadcastOnly() && Objects.equals(localServerAddress, target.getTargetServerAddress())) {
            ClientHelper.deliverLocalBroadcast(metadata.getAppKey(), packet);
            return;
        }
        MessageHelper.asyncSendMessageWithoutInterceptor(packet, target);
    }
}
