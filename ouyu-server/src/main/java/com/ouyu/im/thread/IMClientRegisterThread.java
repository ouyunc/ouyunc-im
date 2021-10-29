package com.ouyu.im.thread;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.*;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.encrypt.Encrypt;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.protocol.Protocol;
import com.ouyu.im.serialize.Serializer;
import com.ouyu.im.utils.MapUtil;
import com.ouyu.im.utils.SnowflakeUtil;
import com.ouyu.im.utils.SocketAddressUtil;
import com.ouyu.im.utils.TimeUtil;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description: 在集群中内置客户端的后置处理
 * @Version V1.0
 **/
public class IMClientRegisterThread implements Runnable {
    private static Logger log = LoggerFactory.getLogger(IMClientRegisterThread.class);

    /**
     * 开始执行的时间
     */
    private static final Instant beginTime = Instant.now();
    /**
     * @param
     * @return void
     * @Author fangzhenxun
     * @Description 处理数据
     */
    @Override
    public void run() {

        // 获取所有注册表中的key
        Set<Map.Entry<InetSocketAddress, ChannelPool>> availableGlobalServer = MapUtil.mergerMaps(IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_CONNECTS_CACHE.asMap()).entrySet();
        Iterator<Map.Entry<InetSocketAddress, ChannelPool>> socketAddressChannelIterator = availableGlobalServer.iterator();
        while (socketAddressChannelIterator.hasNext()) {
            Map.Entry<InetSocketAddress, ChannelPool> socketAddressChannelPoolEntry = socketAddressChannelIterator.next();
            InetSocketAddress inetSocketAddress = socketAddressChannelPoolEntry.getKey();
            ChannelPool channelPool = socketAddressChannelPoolEntry.getValue();
            Future<Channel> channelFuture = channelPool.acquire();
            channelFuture.addListener(new FutureListener<Channel>() {
                // 添加监听器
                @Override
                public void operationComplete(Future<Channel> future) throws Exception {
                    // 判断是否已经处理完
                    if (future.isDone()) {
                        // 判断是否成功连接，获取channel
                        if (future.isSuccess()) {
                            // 从池中获取channel (注意此时channel pool 中可能还没有channel，或者已经存在有标签的channel)
                            Channel channel = future.getNow();
                            // 将该通道打上标签,(如果该通道channel 上有标签则不需要再打标签)
                            AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_POOL);
                            final Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                            if (channelPoolHashCode == null) {
                                channel.attr(channelTagPoolKey).set(channelPool.hashCode());
                            }
                            // 给暂未连接的的服务（不在注册表中）进行重试连接发送syn去握手，需要回复ack
                            //    public ImMessage(String from, String to, int contentType, int contentMime, byte[] content, long createTime) {
                            Message message = new Message(IMServerContext.LOCAL_ADDRESS, SocketAddressUtil.convert2HostPort(inetSocketAddress), MessageContentEnum.TEXT_CONTENT.code(),  ImConstant.SYN, TimeUtil.currentTimestamp());
                            //  ==============string = ACK=========
                            //     packet            message
                            // protoStuff 150b         80b  内部心跳只用protoStuff序列化/反序列化
                            // protoBuf   156b         83b
                            // kryo       140b         112b
                            // json       355b         184b
                            // hessian2   357b         221b
                            // hessian    430b         235b
                            // fst        650b         315b
                            // jdk        500b         346b
                            Packet packet = new Packet(Protocol.OU_YU_IM.getProtocol(), Protocol.OU_YU_IM.getVersion(), SnowflakeUtil.nextId(), DeviceEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), InetAddress.getLocalHost().getHostAddress(), MessageEnum.SYN_ACK.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(),  message);
                            // 将信息写出去
                            channel.writeAndFlush(packet);
                            // 用完后进行释放掉
                            channelPool.release(channel);
                        } else {
                            final Throwable cause = future.cause();
                            log.error("更新服务注册表有异常,原因：{}", cause.getMessage());
                            //这里如果服务连接不上会自动close(),从而触发自定义的关闭事件
                            // 下线服务
                            AtomicInteger missAckTimes = IMServerContext.MISS_ACK_TIMES_CACHE.get(inetSocketAddress);
                            // 3次没回应，服务下线
                            if (IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.asMap().containsKey(inetSocketAddress) && missAckTimes.incrementAndGet() >= 3) {
                                IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.invalidate(inetSocketAddress);
                                // 检测到socketAddress服务下线，进行处理,@todo 这里可以异步
                                handlerServerOffline(inetSocketAddress);
                            }
                            // 判断该服务所在的集群个数是否小于服务列表的半数（用于解决脑裂）
                            if (IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.asMap().size() < (int)Math.ceil(availableGlobalServer.size()/2.0) && ChronoUnit.MINUTES.between(beginTime, Instant.now()) >= 30) {
                                // 启动服务注销,30分钟后进行检测是否脑裂,系统退出
                                log.error("系统启动自毁程序...");
                                System.exit(0);
                                return;
                            }
                        }

                    }
                }
            });
        }
    }


    /**
     * @Author fangzhenxun
     * @Description @todo 处理服务下线
     * @param inetSocketAddress
     * @return void
     */
    public void handlerServerOffline(InetSocketAddress inetSocketAddress){
        log.warn("正在处理下线服务：{}",SocketAddressUtil.convert2HostPort(inetSocketAddress));




    }

}
