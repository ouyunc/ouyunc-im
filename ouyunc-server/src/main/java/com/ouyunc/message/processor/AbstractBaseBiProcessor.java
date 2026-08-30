package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.processor.BiProcessor;
import com.ouyunc.core.qos.Qos;
import com.ouyunc.message.helper.QosAckHelper;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 基础抽象处理类
 **/
public abstract class AbstractBaseBiProcessor<T extends Number> implements BiProcessor<ChannelHandlerContext, Packet>, Qos {
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseBiProcessor.class);

    /**
     * 类型
     */
    public abstract ProtocolType<? extends T> type();

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository> R repository() {
        return (R) DefaultRepository.INSTANCE;
    }

    /**
     * qos 前置处理，一般用于消息过滤，比如消息是否是重发等
     */
    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        // 判断是否是需要qos以及是否是客户端模式
        Message message = packet.getMessage();
        // 判断是否开启qos
        if (MessageContext.isQosEnable() && packet.getMessageType() == MessageTypeEnum.QOS_DUP.getType() && message.getContentType() == MessageContentTypeEnum.QOS_DUP_CONTENT.getType()) {
            Packet dupPacket = JSON.parseObject(message.getContent(), Packet.class);
            if (dupPacket == null || dupPacket.getMessage() == null) {
                log.warn("QOS_DUP 内容解析失败，按新消息处理: {}", message.getContent());
                return false;
            }
            // 缓存原始 dupPacket 供 qosPostHandle 使用
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_QOS_DUP_ORIGINAL_PACKET, dupPacket);

            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            String channelLoginIdentity = loginClientInfo != null ? loginClientInfo.getIdentity() : null;
            if (repository().checkDup(dupPacket, channelLoginIdentity)) {
                qosPostHandle(ctx, packet);
                return true;
            }
            // 未命中幂等键，按新消息展开处理
            // 将元数据放入重发消息的packet中，否则会丢失相关信息
            Metadata metadata = message.getMetadata();
            dupPacket.getMessage().setMetadata(metadata);
            dupPacket.setPacketId(MessageContext.idGenerator().generateId());
            // 原地写回同一 Packet 引用，保证上游 fireChannelRead(packet) 拿到展开后的业务包
            packet.copyFrom(dupPacket);
            if (log.isDebugEnabled()) {
                log.debug("qos 客户端模式正在处理客户端重发消息, 重发消息为: {}", packet);
            }
        }
        return false;
    }

    /**
     * qos 后置处理，一般用于发送ack，给发送端确认消息已经到达服务端
     */
    @Override
    public void qosPostHandle(ChannelHandlerContext ctx, Packet packet) {
        QosAckHelper.sendS2cAck(ctx, packet);
    }
}
