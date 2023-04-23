package com.ouyunc.im.protocol;


import cn.hutool.core.date.SystemClock;
import com.ouyunc.im.base.MissingPacket;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.handler.*;
import com.ouyunc.im.innerclient.pool.IMInnerClientPool;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.ReaderWriterUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 协议类型
 * @Version V3.0
 **/
public enum Protocol {

    // 处理ws/wss,这里相当于关键入口
    WS((byte) 1, (byte)1, "websocket 协议，版本号为1") {

        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {
            ctx.pipeline()
                    //10 * 1024 * 1024
                    .addLast(IMConstant.WS_FRAME_AGGREGATOR, new WebSocketFrameAggregator(Integer.MAX_VALUE))
                    //10485760
                    .addLast(IMConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler("/", null, true, Integer.MAX_VALUE))
                    // 转换成包packet,内部消息传递都是以packet 进行处理
                    .addLast(IMConstant.CONVERT_2_PACKET, new Convert2PacketHandler())
                    // 在业务处理之前可以进行登录认证处理，登录认证处理，如果不需要登录处理，可在配置文件中配置，不需要在这里处理
                    .addLast(IMConstant.AUTHENTICATION, new AuthenticationHandler())
                    // 业务处理
                    .addLast(IMConstant.WS_HANDLER, new WsServerHandler())
                    // 开启qos
                    .addLast(IMConstant.QOS_HANDLER, new QosHandler());
            // 判断是否需要开启客户端心跳如果需要则开启客户端心跳，由于心跳消息不需要登录就可以，所以放在登录认证处理器前面
            // 判断是否开启客户端心跳,如果开启才会添加心跳检测处理器
            if (IMServerContext.SERVER_CONFIG.isHeartBeatEnable()) {
                ctx.pipeline()
                        // 添加读写空闲处理器， 添加后，下条消息就可以接收心跳消息了
                        .addAfter(IMConstant.LOG, IMConstant.HEART_BEAT_IDLE, new IdleStateHandler(IMServerContext.SERVER_CONFIG.getHeartBeatTimeout(),0,0))
                        // 处理心跳的以及相关逻辑都放在这里处理
                        .addAfter(IMConstant.CONVERT_2_PACKET, IMConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
            }
            // 移除协议分发器
            ctx.pipeline().remove(IMConstant.HTTP_DISPATCHER_HANDLER);
            // 调用当前handler的下一个handle的active，注意与ctx.pipeline().fireChannelActive()
            ctx.fireChannelActive();
        }

        /**
         * @Author fangzhenxun
         * @Description ws/wss 最终发送的消息方法
         * @param packet
         * @param to  接受者唯一标识，这里了也可以从packet获取但是还需要，进行消息的判断与解析（主要不同是，序列化不同的消息类型）
         * @return void
         */
        @Override
        public void doSendMessage(Packet packet, String to) {
            try{
                // 这里可以处理具体发送多设备的在线消息，离线以及在线
                // 需要考虑多设备登录？离线消息如何嵌入？，多种消息类型进行统一处理
                final ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
                ReaderWriterUtil.writePacketInByteBuf(packet, byteBuf);
                //从用户注册表中，获取用户对应的channel然后将消息写出去
                IMServerContext.USER_REGISTER_TABLE.get(to).writeAndFlush(new BinaryWebSocketFrame(byteBuf));
            }catch (Exception e) {
                log.error("消息packet: {} 发送给用户: {} 失败!", packet, to);
                Message message = (Message) packet.getMessage();
                // 消息丢失
                if (packet.getMessageType() == MessageEnum.IM_PRIVATE_CHAT.getValue() || packet.getMessageType() == MessageEnum.IM_GROUP_CHAT.getValue()) {
                    // 对于多端的情况
                    long now = SystemClock.now();
                    IMServerContext.MISSING_MESSAGES_CACHE.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.FAIL + CacheConstant.FROM + message.getFrom() + CacheConstant.COLON + CacheConstant.TO + to , new MissingPacket(packet, IMServerContext.SERVER_CONFIG.getLocalServerAddress(), now), now);
                }
            }
        }
    },

    //处理 http/https
    HTTP((byte)3, (byte)1, "http协议，版本号为1"){
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {

        }

        @Override
        public void doSendMessage(Packet packet, String to) {
            // do something
        }
    },

    // 目前该协议不对外开放只作为集群内部协议使用，可以对接jt 818,或者其他物联网的通信，字节扩充，
    OUYUC((byte)5, (byte)1, "自定义ouyunc协议，版本号为1"){
        @Override
        public void doDispatcher(ChannelHandlerContext ctx) {
            ctx.pipeline()
                    // 上一个packet编解码处理器，处理后，会在这里交给包转换器来转换
                    // 转换成包packet，这里为了做兼容客户端心跳
                    .addLast(IMConstant.CONVERT_2_PACKET, new Convert2PacketHandler())
                    // 集群内部/外部业务处理
                    .addLast(IMConstant.OUYUNC_IM_HANDLER, new OuyuncServerHandler())
                    // 移除协议分发器
                    .remove(IMConstant.PACKET_DISPATCHER_HANDLER);
            // 调用下一个handle的active
            ctx.fireChannelActive();
        }

        /**
         * 直接获取对应的channel然后写出去,主要用于集群中心跳
         * @param packet
         * @param to
         */
        @Override
        public void doSendMessage(Packet packet, String to) {
            InetSocketAddress remoteInetSocketAddress = SocketAddressUtil.convert2SocketAddress(to);
            ChannelPool channelPool = MapUtil.mergerMaps(IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.asMap()).get(remoteInetSocketAddress);;
            if (channelPool == null) {
                log.warn("有新的服务加入集群，正在尝试与其确认ack,: {}",to);
                channelPool = IMInnerClientPool.singleClientChannelPoolMap.get(remoteInetSocketAddress);
            }
            ChannelPool finalChannelPool = channelPool;
            Future<Channel> channelFuture = finalChannelPool.acquire();
            channelFuture.addListener(new FutureListener<Channel>(){
                @Override
                public void operationComplete(Future<Channel> future) throws Exception {
                    if (future.isDone()) {
                        // 判断是否连接成功
                        if (future.isSuccess()) {
                            Channel channel = future.getNow();
                            // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                            AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_POOL);
                            final Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                            if (channelPoolHashCode == null) {
                                channel.attr(channelTagPoolKey).set(finalChannelPool.hashCode());
                            }
                            // 客户端将数据写出到中介管道中
                            channel.writeAndFlush(packet);
                            // 用完后进行释放掉
                            finalChannelPool.release(channel);
                        }else {
                            // 获取失败
                            Throwable cause = future.cause();
                            log.error("客户端获取channel异常！原因: {}", cause.getMessage());
                        }

                    }
                }
            });
        }
    };


    private byte protocol;
    private byte version;
    private String description;

    Protocol(byte protocol, byte version, String description) {
        this.protocol = protocol;
        this.version = version;
        this.description = description;
    }

    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
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
    private static Logger log = LoggerFactory.getLogger(Protocol.class);

    public abstract void doDispatcher(ChannelHandlerContext ctx);


    public abstract void doSendMessage(Packet packet, String to);
}
