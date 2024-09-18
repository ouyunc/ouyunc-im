package com.ouyunc.message.convert;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author fzx
 * @description packet 转换成packet
 */
public enum PacketPacketConverter implements PacketConverter<Packet>{
    INSTANCE
    ;
    /***
     * @author fzx
     * @description msg 此时有以下几种情况：1，集群中的心跳（不需要处理元数据）,2，外部消息在集群传递（已经被包装过元数据）
     */
    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet packet) {
            return packet;
        }
        return null;
    }

    @Override
    public Packet convertFromPacket(Packet packet) {
        // 这里涉及到集群内部的传递，所以不对元数据信息做清空处理
        if (NativePacketProtocol.OUYUNC.getProtocol() == packet.getProtocol() && NativePacketProtocol.OUYUNC.getProtocolVersion() == packet.getProtocolVersion()) {
            return packet;
        }
        return null;
    }
}
