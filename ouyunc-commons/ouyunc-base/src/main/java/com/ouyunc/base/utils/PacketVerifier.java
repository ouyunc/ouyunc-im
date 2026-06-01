package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Packet} 协议头与消息体字段校验（编解码层安全边界）。
 */
public final class PacketVerifier {

    private static final Logger log = LoggerFactory.getLogger(PacketVerifier.class);

    private PacketVerifier() {
    }

    /**
     * @return true 表示协议包字段合法
     */
    public static boolean verify(Packet packet) {
        if (packet == null) {
            log.warn("Packet 校验失败: packet 为空");
            return false;
        }
        if (!PacketMagicUtil.isPacketMagic(packet.getMagic())) {
            log.warn("Packet 校验失败: 非法魔数, packetId={}", packet.getPacketId());
            return false;
        }
        if (packet.getPacketId() <= 0) {
            log.warn("Packet 校验失败: 非法 packetId={}", packet.getPacketId());
            return false;
        }
        if (!isKnownProtocol(packet.getProtocol(), packet.getProtocolVersion())) {
            log.warn("Packet 校验失败: 未知协议 protocol={}, version={}, packetId={}",
                    packet.getProtocol(), packet.getProtocolVersion(), packet.getPacketId());
            return false;
        }
        if (NetworkEnum.getNetworkEnumByValue(packet.getNetworkType()) == null) {
            log.warn("Packet 校验失败: 未知 networkType={}, packetId={}", packet.getNetworkType(), packet.getPacketId());
            return false;
        }
        if (!isKnownEncryptType(packet.getEncryptType())) {
            log.warn("Packet 校验失败: 未知 encryptType={}, packetId={}", packet.getEncryptType(), packet.getPacketId());
            return false;
        }
        if (!isKnownSerializeAlgorithm(packet.getSerializeAlgorithm())) {
            log.warn("Packet 校验失败: 未知 serializeAlgorithm={}, packetId={}",
                    packet.getSerializeAlgorithm(), packet.getPacketId());
            return false;
        }
        if (!isKnownMessageType(packet.getMessageType())) {
            log.warn("Packet 校验失败: 未知 messageType={}, packetId={}", packet.getMessageType(), packet.getPacketId());
            return false;
        }
        if (packet.getMessageLength() < 0
                || packet.getMessageLength() > MessageConstant.MAX_MESSAGE_CONTENT_LENGTH) {
            log.warn("Packet 校验失败: 非法 messageLength={}, packetId={}",
                    packet.getMessageLength(), packet.getPacketId());
            return false;
        }
        Message message = packet.getMessage();
        if (message == null) {
            log.warn("Packet 校验失败: message 为空, packetId={}", packet.getPacketId());
            return false;
        }
        if (message.getMetadata() == null) {
            log.warn("Packet 校验失败: metadata 为空, packetId={}", packet.getPacketId());
            return false;
        }
        if (!isKnownContentType(message.getContentType())) {
            log.warn("Packet 校验失败: 未知 contentType={}, packetId={}",
                    message.getContentType(), packet.getPacketId());
            return false;
        }
        return true;
    }

    private static boolean isKnownProtocol(byte protocol, byte protocolVersion) {
        for (ProtocolTypeEnum protocolType : ProtocolTypeEnum.values()) {
            if (protocolType.getProtocol() == protocol && protocolType.getProtocolVersion() == protocolVersion) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownEncryptType(byte encryptType) {
        for (Encrypt.SymmetryEncrypt encrypt : Encrypt.SymmetryEncrypt.values()) {
            if (encrypt.getValue() == encryptType) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownSerializeAlgorithm(byte serializeAlgorithm) {
        for (Serializer serializer : Serializer.values()) {
            if (serializer.getValue() == serializeAlgorithm) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownMessageType(byte messageType) {
        for (MessageTypeEnum type : MessageTypeEnum.values()) {
            if (type.getType() == messageType) {
                return true;
            }
        }
        for (OuyuncMessageTypeEnum type : OuyuncMessageTypeEnum.values()) {
            if (type.getType() == messageType) {
                return true;
            }
        }
        for (MqttMessageTypeEnum type : MqttMessageTypeEnum.values()) {
            if (type.getType() == messageType) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownContentType(int contentType) {
        for (MessageContentTypeEnum type : MessageContentTypeEnum.values()) {
            if (type.getType() == contentType) {
                return true;
            }
        }
        for (OuyuncMessageContentTypeEnum type : OuyuncMessageContentTypeEnum.values()) {
            if (type.getType() == contentType) {
                return true;
            }
        }
        for (MqttMessageContentTypeEnum type : MqttMessageContentTypeEnum.values()) {
            if (type.getType() == contentType) {
                return true;
            }
        }
        return false;
    }
}
