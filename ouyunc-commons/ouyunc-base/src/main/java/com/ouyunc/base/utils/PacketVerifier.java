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
 * 帧结构检查：魔数、线上协议版本、长度、算法、消息体、类型码是否登记。
 * <p>不管连接是谁、metadata、登录或路由。那些分别由 Channel 守卫和对应 Processor 处理。</p>
 */
public final class PacketVerifier {

    private static final Logger log = LoggerFactory.getLogger(PacketVerifier.class);

    private PacketVerifier() {
    }

    /**
     * @return true 表示这一帧字段能编解码
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
        if (!isKnownWireProtocol(packet.getProtocol(), packet.getProtocolVersion())) {
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
        if (!isRegisteredMessageType(packet.getMessageType())) {
            log.warn("Packet 校验失败: 未登记的 messageType={}, packetId={}",
                    packet.getMessageType(), packet.getPacketId());
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
        if (!isRegisteredContentType(message.getContentType())) {
            log.warn("Packet 校验失败: 未登记的 contentType={}, packetId={}",
                    message.getContentType(), packet.getPacketId());
            return false;
        }
        return true;
    }

    /** ZERO 只是类型通配标志，不是线上协议。 */
    private static boolean isKnownWireProtocol(byte protocol, byte protocolVersion) {
        for (ProtocolTypeEnum protocolType : ProtocolTypeEnum.values()) {
            if (protocolType == ProtocolTypeEnum.ZERO) {
                continue;
            }
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

    private static boolean isRegisteredMessageType(byte messageType) {
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

    private static boolean isRegisteredContentType(int contentType) {
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
