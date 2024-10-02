package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;

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
            if (MessageConstant.PACKET_MAGIC[i] != magicBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
