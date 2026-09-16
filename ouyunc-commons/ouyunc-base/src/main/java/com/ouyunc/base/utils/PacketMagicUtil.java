package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.buffer.ByteBuf;

/**
 * packet 魔数工具类
 */
public class PacketMagicUtil {


    /**
     * 是否是packet magic
     * @param magicBytes
     * @return
     */
    public static boolean isPacketMagic(byte[] magicBytes) {
        if (magicBytes == null || magicBytes.length != MessageConstant.MAGIC_BYTE_LENGTH) {
            return false;
        }
        for (int i = 0; i < MessageConstant.MAGIC_BYTE_LENGTH; i++) {
            if (MessageConstant.PACKET_MAGIC_BYTES[i] != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 窥探 ByteBuf：魔数合法且紧随的协议号等于 {@code protocol}（不推进 readerIndex）。
     * <p>用于协议分发阶段区分集群 {@code OUYUNC} 与客户端 {@code OUYUNC_CLIENT}。</p>
     */
    public static boolean matchesPacketProtocol(ByteBuf in, byte protocol) {
        if (in == null || in.readableBytes() < MessageConstant.MAGIC_BYTE_LENGTH + 1) {
            return false;
        }
        int readerIndex = in.readerIndex();
        byte[] magicBytes = new byte[MessageConstant.MAGIC_BYTE_LENGTH];
        in.getBytes(readerIndex, magicBytes);
        if (!isPacketMagic(magicBytes)) {
            return false;
        }
        return in.getByte(readerIndex + MessageConstant.MAGIC_BYTE_LENGTH) == protocol;
    }
}
