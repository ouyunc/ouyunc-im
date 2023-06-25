package com.ouyunc.im.utils;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * @Author fangzhenxun
 * @Description: packet编解码工具类
 **/
public class ReaderWriterUtil {
    private static Logger log = LoggerFactory.getLogger(ReaderWriterUtil.class);

    /**
     * @Author fangzhenxun
     * @Description 将 ByteBuf读进packet
     * @param in
     * @return com.ouyu.im.packet.Packet
     */
    public static Packet readByteBuf2Packet(ByteBuf in) {
        if (in == null || !in.isReadable()) {
            log.error("定长解码器LengthFieldBasedFrameDecoder===>packetCodec中缓冲区不可读！");
            throw new IMException("定长解码器LengthFieldBasedFrameDecoder===>packetCodec中缓冲区不可读！");
        }
        // 判断可读长度必须大于基本长度，如果可读字节小于协议基础长度16个字节，则说明出现半包，上面的定长解码器有问题
        if (in.readableBytes() < IMConstant.PACKET_BASE_LENGTH) {
            log.error("定长解码器LengthFieldBasedFrameDecoder===>在协议分发时出现异常！");
            throw new IMException("定长解码器LengthFieldBasedFrameDecoder===>在协议分发时出现异常！");
        }
        //跳过魔数 1个字节，当然可以读取魔数判断是否是符合要求，这里不做校验
        in.skipBytes(IMConstant.MAGIC_BYTES);
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
        //ip4地址4个字节， 将int类型的ip转成string
        final String ip = Ip4Util.int2Ip(in.readInt());

        //消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
        final byte encryptType = in.readByte();
        //序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
        final byte serializeAlgorithm = in.readByte();
        //消息类型，1个字节
        final byte messageType = in.readByte();
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
        return new Packet(protocol, protocolVersion, packetId, deviceType, networkType, ip, encryptType,serializeAlgorithm, messageType, messageLength, message);
    }


    /**
     * @Author fangzhenxun
     * @Description 将packet 写出到bytebuf
     * @param packet
     * @return void
     */
    public static void writePacketInByteBuf(Packet packet, ByteBuf out) {
        //魔数 1个字节
        out.writeByte(packet.getMagic());
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
        //发送方ip4， 4个字节
        out.writeInt(Ip4Util.ip2Int(packet.getIp()));
        //message消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密
        out.writeByte(packet.getEncryptType());
        //序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf)
        out.writeByte(packet.getSerializeAlgorithm());
        //消息类型 1 个字节，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
        out.writeByte(packet.getMessageType());
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
     * @Author fangzhenxun
     * @Description 其他消息/协议类型转packet
     * @param t
     * @param function
     * @return R
     */
    public static<T> Packet convertOther2Packet(T t, Function<T,Packet> function) {
        return function.apply(t);
    }

    /**
     * @Author fangzhenxun
     * @Description packet 转其他数据
     * @param t
     * @param function
     * @return R
     */
    public static<R> R convertPacket2Other(Packet t, Function<Packet,R> function) {
        return function.apply(t);
    }
}
