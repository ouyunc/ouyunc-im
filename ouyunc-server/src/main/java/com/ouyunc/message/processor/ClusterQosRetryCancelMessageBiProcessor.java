package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.content.QosRetryCancelContent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.schedule.QosRetryScheduler;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 集群内部控制包：落地节点把已校验的 C2S ACK 转回始发节点，取消该端 SERVER QoS 下行重试。
 * 只走集群 Channel，不走客户端登录鉴权。客户端 ACK 仍由 {@link QosC2SMessageBiProcessor} 处理。
 */
public final class ClusterQosRetryCancelMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(ClusterQosRetryCancelMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return OuyuncMessageTypeEnum.QOS_RETRY_CANCEL;
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.fromRunnable(() -> {
            if (packet == null || packet.getMessage() == null) {
                return;
            }
            String dest = MessageHelper.clusterDest(packet);
            if (StringUtils.isBlank(dest)) {
                log.warn("集群 QOS_RETRY_CANCEL 缺少 Target.targetServerAddress packetId={}", packet.getPacketId());
                return;
            }
            String local = MessageServerContext.serverProperties().getLocalServerAddress();
            if (!dest.equals(local)) {
                MessageHelper.sendClusterInternal(packet, dest);
                return;
            }
            QosRetryCancelContent content = JSON.parseObject(
                    packet.getMessage().getContent(), QosRetryCancelContent.class);
            if (content == null) {
                log.warn("集群 QOS_RETRY_CANCEL 载荷解析失败 packetId={}", packet.getPacketId());
                return;
            }
            QosRetryScheduler.onClusterCancel(content);
        });
    }
}
