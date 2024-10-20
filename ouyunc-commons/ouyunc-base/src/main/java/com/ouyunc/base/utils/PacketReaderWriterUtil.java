package com.ouyunc.base.utils;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;


/**
 * @Author fzx
 * @Description: packet编解码工具类
 **/
public class PacketReaderWriterUtil {
    private static final Logger log = LoggerFactory.getLogger(PacketReaderWriterUtil.class);

    /**
     * @Author fzx
     * @Description 将 ByteBuf读进packet
     */
    public static Packet readByteBuf2Packet(ByteBuf in) {
        if (in == null || !in.isReadable()) {
            log.error("定长解码器LengthFieldBasedFrameDecoder===>packetCodec中缓冲区不可读！");
            throw new RuntimeException("定长解码器LengthFieldBasedFrameDecoder===>packetCodec中缓冲区不可读！");
        }
        // 判断可读长度必须大于基本长度，如果可读字节小于协议基础长度16个字节，则说明出现半包，上面的定长解码器有问题
        if (in.readableBytes() < MessageConstant.PACKET_BASE_LENGTH) {
            log.error("定长解码器LengthFieldBasedFrameDecoder===>在协议分发时出现异常！");
            throw new RuntimeException("定长解码器LengthFieldBasedFrameDecoder===>在协议分发时出现异常！");
        }
        // 读取魔数判断是否是符合要求
        byte[] magicBytes = new byte[MessageConstant.MAGIC_BYTE_LENGTH];
        in.readBytes(magicBytes);
        if (!PacketMagicUtil.isPacketMagic(magicBytes)) {
            log.error("非法魔数:{}",magicBytes);
            throw new MessageException("非法魔数！");
        }
        //包协议， 1个字节
        final byte protocol = in.readByte();
        //协议版本号，1个字节
        final byte protocolVersion = in.readByte();
        //协议包id 8个字节
        final long packetId = in.readLong();
        //设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
        final byte deviceType = in.readByte();
        //客户端网络类型 1个字节，有线，wifi, 5g，4g, 3g，2g， 其他
        final byte networkType = in.readByte();
        //消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
        final byte encryptType = in.readByte();
        //序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
        final byte serializeAlgorithm = in.readByte();
        //消息类型，1个字节
        final byte messageType = in.readByte();
        //保留位，1个字节
        final byte retain = in.readByte();
        //加密后的消息长度.4个字节
        final int messageLength = in.readInt();
        byte[] messageContentBytes = new byte[messageLength];
        //将消息内容n个字节读到字节数组中
        in.readBytes(messageContentBytes);
        //判断是否需要加密，何种算法加密
        final Encrypt.SymmetryEncrypt encryptEnum = Encrypt.SymmetryEncrypt.prototype(encryptType);
        final byte[] decryptMessageBytes = encryptEnum.decrypt(messageContentBytes, byte[].class);
        // 得到一个完整的包,需要根据具体的消息类型获取消息class
        final Message message = Serializer.prototype(serializeAlgorithm).deserialize(decryptMessageBytes, Message.class);
        // 将解码后的数据添加集合中
        return new Packet(protocol, protocolVersion, packetId, deviceType, networkType, encryptType,serializeAlgorithm, messageType, retain, messageLength, message);
    }


    /**
     * @Author fzx
     * @Description 将packet 写出到bytebuf
     */
    public static void writePacketInByteBuf(Packet packet, ByteBuf out) {
        //魔数 1个字节
        out.writeBytes(packet.getMagic());
        //包协议， 1个字节
        out.writeByte(packet.getProtocol());
        //协议版本号，1个字节
        out.writeByte(packet.getProtocolVersion());
        //协议包id 8个字节
        out.writeLong(packet.getPacketId());
        //发送方设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
        out.writeByte(packet.getDeviceType());
        //发送方网络类型1个字节： 有线，wifi, 5g，4g, 3g，2g， 其他
        out.writeByte(packet.getNetworkType());
        //message消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
        out.writeByte(packet.getEncryptType());
        //序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
        out.writeByte(packet.getSerializeAlgorithm());
        //消息类型 1 个字节，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
        out.writeByte(packet.getMessageType());
        //保留位，1个字节
        out.writeByte(packet.getRetain());
        //将具体的消息序列化
        final Serializer serializer = Serializer.prototype(packet.getSerializeAlgorithm());
        // 注意：这里序列化的是消息message, 没有将packet整个序列化
        final byte[] messageBytes = serializer.serialize(packet.getMessage());
        //判断是否需要加密，何种算法加密
        final Encrypt.SymmetryEncrypt encryptEnum = Encrypt.SymmetryEncrypt.prototype(packet.getEncryptType());
        final byte[] encryptMessageBytes = encryptEnum.encrypt(messageBytes);
        //加密后的消息长度.4个字节
        out.writeInt(encryptMessageBytes.length);
        //加密后的消息内容，n个字节, 不同的消息类型有可能是不同的数据内容
        out.writeBytes(encryptMessageBytes);
    }

    /**
     * @Author fzx
     * @Description 其他消息/协议类型转packet
     */
    public static<T> Packet convertObject2Packet(T t, Function<T,Packet> function) {
        return function.apply(t);
    }

    /**
     * @Author fzx
     * @Description packet 转其他数据
     */
    public static<R> R convertPacket2Object(Packet t, Function<Packet,R> function) {
        return function.apply(t);
    }
}
