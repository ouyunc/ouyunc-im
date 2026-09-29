package com.ouyunc.message.convert;

import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author fzx
 * @description packet 转换器， 给定业务消息，转换成packet 类型的实体,注意如果转换不了请返回null
 */
public interface PacketConverter<T> {

    /***
     * @author fzx
     * @description 将业务消息T转换成packet, 并初始化元数据，注意：需要处理 message 中的元数据，进行必要信息的填充
     */
    Packet convertToPacket(ChannelHandlerContext ctx, Object msg);

    /**
     * 按最终目标 Channel 已绑定的权威协议，将内部 Packet 转成线协议对象。
     *
     * @param protocol 最终目标 Channel 的协议，禁止从路由 Target 推断
     * @param packet 内部消息
     * @return 当前转换器不支持该协议时返回 null
     */
    T convertFromPacket(Protocol protocol, Packet packet);
}
