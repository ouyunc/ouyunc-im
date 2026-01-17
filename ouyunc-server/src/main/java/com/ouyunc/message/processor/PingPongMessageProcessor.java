package com.ouyunc.message.processor;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 外部客户端心跳消息,这里不做登录的校验？
 **/
public final class PingPongMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(PingPongMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.PING_PONG;
    }



    /***
     * @author fzx
     * @description 核心业务逻辑处理
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("PingPongMessageProcessor 正在处理外部客户端心跳 {} ...", packet);
        }
        // 可能在三次之内再次发起心跳，此时需要清除 之前心跳超时次数的历史记录
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES, null);
        // 发送pong
        // 处理心跳消息
        Message heartBeatMessage = packet.getMessage();
        Metadata metadata = heartBeatMessage.getMetadata();
        String from = heartBeatMessage.getFrom();
        if (StringUtils.isBlank(from)) {
            log.error("心跳发送方不能为空！{}",  packet);
            return;
        }
        heartBeatMessage.setId(MessageContext.idGenerator().generateIdStr());
        heartBeatMessage.setFrom(null);
        heartBeatMessage.setTo(from);
        heartBeatMessage.setContent(null);
        heartBeatMessage.setContentType(MessageContentTypeEnum.PING_PONG_CONTENT.getType());
        heartBeatMessage.setCreateTime(TimeUtil.currentTimeMillis());
        packet.setPacketId(MessageContext.idGenerator().generateId());
        // 写回的是websocket还是其他类型的数据
        MessageHelper.asyncSendMessage(packet, Target.newBuilder().targetIdentity(from).deviceType(MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType())).targetServerAddress(MessageServerContext.serverProperties().getLocalServerAddress()).protocol(packet.getProtocol()).protocolVersion(packet.getProtocolVersion()).build());
    }
}
