package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.cluster.RelationCacheInvalidateSupport;
import com.ouyunc.message.helper.PacketChannelWriter;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群入站：清本机关系 Caffeine，不再向外扇出。
 */
public final class RelationCacheInvalidateMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidateMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return OuyuncMessageTypeEnum.RELATION_CACHE_INVALIDATE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        PacketChannelWriter.fireChannelRead(ctx, packet);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (packet == null || packet.getMessage() == null || StringUtils.isBlank(packet.getMessage().getContent())) {
            return;
        }
        try {
            RelationCacheInvalidateEvent event = JSON.parseObject(
                    packet.getMessage().getContent(), RelationCacheInvalidateEvent.class);
            RelationCacheInvalidateSupport.applyLocal(event);
        } catch (Exception e) {
            log.error("集群关系本机缓存失效处理失败", e);
        }
    }
}
