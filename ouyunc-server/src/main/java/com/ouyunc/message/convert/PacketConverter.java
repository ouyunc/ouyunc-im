package com.ouyunc.message.convert;

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

    /***
     * @author fzx
     * @description 将packet转成业务消息T
     */
    T convertFromPacket(Packet packet);
}
