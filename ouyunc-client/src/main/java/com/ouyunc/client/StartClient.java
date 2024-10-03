package com.ouyunc.client;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.codec.PacketCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author fzx
 * @description 客户端启动
 */
public class StartClient {    private static final ScheduledExecutorService SCHEDULED_EVENT_EXECUTORS = Executors.newScheduledThreadPool(16, new BasicThreadFactory.Builder().namingPattern("client-heart-hart-pool-%d").build());

    public static void main(String[] args) throws InterruptedException {
//        MessageClient messageClient = new DefaultMessageClient();
//        messageClient.configure(null);
//        LoginContent loginContent = new LoginContent();
//        loginContent.setAppKey("ouyunc");
//        loginContent.setIdentity("18856895462");
//        loginContent.setDeviceType(DeviceTypeEnum.PC);
//        Message message = new Message("123456:1", "18856895462", WsMessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType(), JSON.toJSONString(loginContent), Clock.systemUTC().millis());
//        Packet packet = new Packet(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getDeviceTypeValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), WsMessageTypeEnum.LOGIN.getType(), message);
//
//        MessageClientTemplate.syncSendMessage(packet);
//
//        SCHEDULED_EVENT_EXECUTORS.schedule(new Runnable() {
//            @Override
//            public void run() {
//                LoginContent loginContent = new LoginContent();
//                loginContent.setAppKey("ouyunc");
//                loginContent.setIdentity("18856895462");
//                loginContent.setDeviceType(DeviceTypeEnum.PC);
//                Message message = new Message("123456:1", "18856895462", WsMessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType(), JSON.toJSONString(loginContent), Clock.systemUTC().millis());
//                Packet packet = new Packet(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getDeviceTypeValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), WsMessageTypeEnum.LOGIN.getType(), message);
//
//                //MessageClientTemplate.syncSendMessage(packet);
//            }
//        }, 10, TimeUnit.SECONDS);
//
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new LoggingHandler());
                        socketChannel.pipeline().addLast(new PacketCodec());
                        socketChannel.pipeline().addLast(new SimpleChannelInboundHandler() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                Message message = new Message("12", "192.168.0.113:6001", OuyuncMessageContentTypeEnum.SYN_CONTENT.getType(), TimeUtil.currentTimeMillis());
                                //  ==============针对以上packet 几种序列化对比: string = SYN=========
                                //     packet            message
                                // protoStuff 150b         80b  内部心跳只用protoStuff序列化/反序列化
                                // protoBuf   156b         83b
                                // kryo       140b         112b
                                // json       355b         184b
                                // hessian2   357b         221b
                                // hessian    430b         235b
                                // fst        650b         315b
                                // jdk        500b         346b
                                Packet packet = new Packet((byte) 3, (byte) 1, 123L, DeviceTypeEnum.PC.getValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), OuyuncMessageTypeEnum.SYN_ACK.getType(), message);

                                ctx.writeAndFlush(packet);
                            }

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, Object o) throws Exception {

                            }
                        });
                    }
                });
        Channel ch = b.connect("192.168.0.113", 6003).sync().channel();
    }
}
