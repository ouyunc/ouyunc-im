package com.ouyunc.message.convert;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description websocket协议 二进制帧转换成packet
 */
public enum BinaryWebSocketFramePacketConverter implements PacketConverter<BinaryWebSocketFrame>{
    INSTANCE
    ;
    private static final Logger log = LoggerFactory.getLogger(BinaryWebSocketFramePacketConverter.class);

    /***
     * @author fzx
     * @description 需要处理 消息元数据的初始化
     */
    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
            Packet packet = PacketReaderWriterUtil.readByteBuf2Packet(binaryWebSocketFrame.content());
            if (packet == null || packet.getMessage() == null) {
                log.warn("WebSocket Packet 缺少 message，关闭连接 channel={}",
                        ctx.channel().id().asShortText());
                ctx.close();
                throw new MessageException("WebSocket Packet 缺少 message");
            }
            // 获取消息
            Message message = packet.getMessage();
            // 获取元数据
            // 外部客户端携带的 metadata 不可信，由服务端重新建立内部上下文。
            Metadata metadata = new Metadata();
            // 判断如果不是集群中的传递消息，则进行以下处理
            if (metadata.isLocalIngress()) {
                // 设置该消息发送者当前登录所属的平台 appKey
                // 设置默认的appKey
                if (MessageTypeEnum.LOGIN.getType() == packet.getMessageType()) {
                    LoginContent loginContent = JSON.parseObject(message.getContent(), LoginContent.class);
                    if (loginContent == null || StringUtils.isBlank(loginContent.getAppKey())) {
                        log.error("客户端:{} 登录内容无法解析或缺少 appKey", message.getFrom());
                        ctx.close();
                        throw new MessageException("客户端:" + message.getFrom() + " 登录内容无法解析");
                    }
                    metadata.ensureIngress().setAppKey(loginContent.getAppKey());
                }else {
                    // 不是登录类型的消息，说明该客户端已经登录，可以从当前通道获取用户appKey
                    LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                    if (loginClientInfo == null) {
                        log.error("客户端:{} 未登录，请先登录", message.getFrom());
                        ctx.close();
                        throw new MessageException("客户端:"+message.getFrom()+" 未登录，请先登录");
                    }
                    metadata.ensureIngress().setAppKey(loginClientInfo.getAppKey());
                }
                // 获取客户端真实ip
                metadata.ensureIngress().setClientIp(IpUtil.getIp(ctx));
                // 外部入站的来源由服务端覆盖赋值；集群透传不进入此分支。
                metadata.ensureIngress().setOriginServerAddress(MessageContext.messageProperties.getLocalServerAddress());
                // 设置服务器时间
                metadata.ensureIngress().setServerTime(TimeUtil.currentTimeMillis());
                metadata.ensureIngress().setIngressSource(IngressSourceEnum.IM);
            }
            message.setMetadata(metadata);
            // 设置服务端生成的消息id，以服务端的主键为准
            packet.setPacketId(MessageContext.idGenerator().generateId());
            return packet;
        }
        return null;
    }

    /***
     * @author fzx
     * @description 将packet转换成BinaryWebSocketFrame
     */
    @Override
    public BinaryWebSocketFrame convertFromPacket(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || packet.getMessage().getMetadata().getClusterRoute() == null) {
            return null;
        }
        Target target = packet.getMessage().getMetadata().getClusterRoute().getTarget();
        if (target != null && target.getProtocol() == NativePacketProtocol.WS.getProtocol() && target.getProtocolVersion() == NativePacketProtocol.WS.getProtocolVersion()) {
            // 只复制客户端协议字段，不深拷贝随后必然丢弃的内部 Metadata。
            Packet outbound = packet.copyForExternalDelivery();
            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
            try {
                PacketReaderWriterUtil.writePacketInByteBuf(outbound, byteBuf);
                return new BinaryWebSocketFrame(byteBuf);
            } catch (RuntimeException | Error e) {
                // frame 尚未接管引用，转换失败时必须释放，避免 Netty 直接内存泄漏。
                byteBuf.release();
                throw e;
            }
        }
        return null;
    }
}
