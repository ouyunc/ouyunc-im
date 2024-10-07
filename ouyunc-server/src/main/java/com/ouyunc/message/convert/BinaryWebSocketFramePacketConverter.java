package com.ouyunc.message.convert;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.WsMessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;

/**
 * @author fzx
 * @description websocket协议 二进制帧转换成packet
 */
public enum BinaryWebSocketFramePacketConverter implements PacketConverter<BinaryWebSocketFrame>{
    INSTANCE
    ;
    /***
     * @author fzx
     * @description 需要处理 消息元数据的初始化
     */
    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
            Packet packet = PacketReaderWriterUtil.readByteBuf2Packet(binaryWebSocketFrame.content());
            // 获取消息
            Message message = packet.getMessage();
            // 获取元数据
            Metadata metadata = message.getMetadata();
            // 处理元数据
            if (metadata == null) {
                metadata = new Metadata();
            }
            // 判断如果不是集群中的传递消息，则进行以下处理
            if (!metadata.isRouted()) {
                // 设置该消息发送者当前登录所属的平台 appKey
                // 设置默认的appKey
                if (WsMessageTypeEnum.LOGIN.getType() == packet.getMessageType()) {
                    LoginContent loginContent = JSON.parseObject(message.getContent(), LoginContent.class);
                    metadata.setAppKey(loginContent.getAppKey());
                }else {
                    // 不是登录类型的消息，说明该客户端已经登录，可以从当前通道获取用户appKey
                    AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                    LoginClientInfo loginClientInfo = ctx.channel().attr(channelTagLoginKey).get();
                    metadata.setAppKey(loginClientInfo.getAppKey());
                }
                // 获取客户端真实ip
                metadata.setClientIp(IpUtil.getIp(ctx));
                // 设置服务器时间
                metadata.setServerTime(TimeUtil.currentTimeMillis());
            }
            message.setMetadata(metadata);
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
        // 将packet 的元数据信息清空（内部辅助数据，不对客户端暴漏）
        // 如果该包是内部协议的包，则进行逻辑处理
        if (NativePacketProtocol.WS.getProtocol() == packet.getProtocol() && NativePacketProtocol.WS.getProtocolVersion() == packet.getProtocolVersion()) {
            // 暂存元数据信息
            Message message = packet.getMessage();
            Metadata metadata = message.getMetadata();
            message.setMetadata(null);
            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
            PacketReaderWriterUtil.writePacketInByteBuf(packet, byteBuf);
            // 在将该元数据设置进去
            message.setMetadata(metadata);
            return new BinaryWebSocketFrame(byteBuf);
        }
        return null;
    }
}
