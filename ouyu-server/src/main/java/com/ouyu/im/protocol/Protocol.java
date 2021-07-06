package com.ouyu.im.protocol;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.handler.AuthenticationHandler;
import com.ouyu.im.handler.OuYuImServerHandler;
import com.ouyu.im.handler.Convert2PacketHandler;
import com.ouyu.im.handler.WsServerHandler;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.utils.ReaderWriterUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * @Author fangzhenxun
 * @Description: 协议类型
 * @Version V1.0
 **/
public enum Protocol {

    WS((short) 1, (byte)1, "websocket 协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {
            ctx.pipeline()
                    //10 * 1024 * 1024
                    .addLast(ImConstant.WS_FRAME_AGGREGATOR, new WebSocketFrameAggregator(Integer.MAX_VALUE))
                    //10485760
                    .addLast(ImConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler("/", null, true, Integer.MAX_VALUE))
                    // 转换成包packet
                    .addLast(ImConstant.CONVERT_2_PACKET, new Convert2PacketHandler())
                    // 登录处理
                    .addLast(ImConstant.AUTHENTICATION, new AuthenticationHandler())
                    // 业务处理
                    .addLast(ImConstant.WS_HANDLER, new WsServerHandler())
                    // 移除协议分发器
                    .remove(ImConstant.HTTP_DISPATCHER_HANDLER);
            // 调用当前handler的下一个handle的active，注意与ctx.pipeline().fireChannelActive()
            ctx.fireChannelActive();
        }

        /**
         * @Author fangzhenxun
         * @Description 发送该协议的消息
         * @param packet
         * @param tos  接受者唯一标识，这里了也可以从packet获取但是还需要，进行消息的判断与解析（主要不同序列化不同的消息类型）
         * @return void
         */
        @Override
        public void doSendMessage(Packet packet, String[] tos) {
            // 这里可以处理具体发送多设备的在线消息，离线以及在线
            // 需要考虑多设备登录？离线消息如何嵌入？，多种消息类型进行统一处理@todo 这里暂时只考虑一个接受者
            final ChannelHandlerContext ctx = IMContext.LOCAL_USER_CHANNEL_CACHE.get(tos[0]);
            final ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
            ReaderWriterUtil.writePacketInByteBuf(packet, byteBuf);
            ctx.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
        }
    },
    WSS((short) 2, (byte)1, "websocket + SSL/TLS协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {

        }

        @Override
        public void doSendMessage(Packet packet, String[] tos) {

        }
    },
    HTTP((short)3, (byte)1, "http协议，版本号为1"){
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {

        }

        @Override
        public void doSendMessage(Packet packet, String[] tos) {

        }
    },
    HTTPS((short)4, (byte)1, "http + SSL/TLS协议，版本号为1"){
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {

        }

        @Override
        public void doSendMessage(Packet packet, String[] tos) {

        }
    },
    // 可以对接jt 818,或者其他物联网的通信，字节扩充
    OU_YU_IM((short)5, (byte)1, "自定义ou-yu-im协议，版本号为1"){
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {
            ctx.pipeline()
                    // 转换成包packet，这里为了做兼容客户端心跳
                    .addLast(ImConstant.CONVERT_2_PACKET, new Convert2PacketHandler())
                    // 登录处理，如果是集群内部消息传递则不处理，否则进行登录处理
                    .addLast(ImConstant.AUTHENTICATION, new AuthenticationHandler())
                    // 集群内部/外部业务处理
                    .addLast(ImConstant.OU_YU_IM_HANDLER, new OuYuImServerHandler())
                    // 移除协议分发器
                    .remove(ImConstant.PACKET_DISPATCHER_HANDLER);
            // 调用下一个handle的active
            ctx.fireChannelActive();
        }

        @Override
        public void doSendMessage(Packet packet, String[] tos) {

        }
    };


    private short protocol;
    private byte version;
    private String description;

    Protocol(short protocol, byte version, String description) {
        this.protocol = protocol;
        this.version = version;
        this.description = description;
    }

    public short getProtocol() {
        return protocol;
    }

    public void setProtocol(short protocol) {
        this.protocol = protocol;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static Protocol prototype(short protocol, byte protocolVersion) {
        for (Protocol protocolEnum : Protocol.values()) {
            if (protocolEnum.protocol == protocol && protocolEnum.version == protocolVersion) {
                return protocolEnum;
            }
        }
        return null;
    }

    public abstract void doDispatcher(ChannelHandlerContext ctx);


    public abstract void doSendMessage(Packet packet, String[] tos);
}
