package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosRetryCancelContent;
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.monitor.QosRetryCancelMetrics;
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
                QosRetryCancelMetrics.invalidPacket();
                return;
            }
            String dest = MessageHelper.clusterDest(packet);
            if (StringUtils.isBlank(dest)) {
                log.warn("集群 QOS_RETRY_CANCEL 缺少 Target.targetServerAddress packetId={}", packet.getPacketId());
                QosRetryCancelMetrics.invalidPacket();
                return;
            }
            String local = MessageServerContext.serverProperties().getLocalServerAddress();
            if (!dest.equals(local)) {
                MessageHelper.sendClusterInternal(packet, dest);
                return;
            }
            QosRetryCancelContent content = parseAndValidate(packet);
            if (content == null) {
                return;
            }
            QosRetryScheduler.onClusterCancel(content);
        });
    }

    private static QosRetryCancelContent parseAndValidate(Packet packet) {
        Message message = packet.getMessage();
        if (message.getContentType() != OuyuncMessageContentTypeEnum.QOS_RETRY_CANCEL_CONTENT.getType()) {
            log.warn("集群 QOS_RETRY_CANCEL contentType 非法 packetId={} contentType={}",
                    packet.getPacketId(), message.getContentType());
            QosRetryCancelMetrics.invalidPacket();
            return null;
        }
        QosRetryCancelContent content;
        try {
            content = JSON.parseObject(message.getContent(), QosRetryCancelContent.class);
        } catch (Exception e) {
            log.warn("集群 QOS_RETRY_CANCEL 载荷 JSON 解析失败 packetId={}", packet.getPacketId(), e);
            QosRetryCancelMetrics.invalidPacket();
            return null;
        }
        if (content == null || content.getPacketId() <= 0
                || StringUtils.isAnyBlank(content.getAppKey(), content.getIdentity())) {
            log.warn("集群 QOS_RETRY_CANCEL 载荷字段非法 packetId={}", packet.getPacketId());
            QosRetryCancelMetrics.invalidPacket();
            return null;
        }
        Metadata metadata = message.getMetadata();
        Target target = metadata == null ? null : metadata.getClusterRoute().getTarget();
        String metaAppKey = metadata == null ? null : metadata.getIngress().getAppKey();
        String targetAppKey = target == null ? null : target.getAppKey();
        if (!content.getAppKey().equals(metaAppKey)
                || (StringUtils.isNotBlank(targetAppKey) && !content.getAppKey().equals(targetAppKey))) {
            log.warn("集群 QOS_RETRY_CANCEL appKey 不一致 packetId={} content={} meta={} target={}",
                    packet.getPacketId(), content.getAppKey(), metaAppKey, targetAppKey);
            QosRetryCancelMetrics.invalidPacket();
            return null;
        }
        if (!DeviceTypeRegistry.supports(content.getAppKey(), content.getDeviceType())) {
            log.warn("集群 QOS_RETRY_CANCEL 设备类型非法 packetId={} deviceType={}",
                    packet.getPacketId(), content.getDeviceType());
            QosRetryCancelMetrics.invalidPacket();
            return null;
        }
        return content;
    }
}
