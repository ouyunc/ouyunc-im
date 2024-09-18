package com.ouyunc.client;

import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.ProtocolType;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.client.base.ChannelPoolKey;
import com.ouyunc.client.pool.MessageClientPool;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author fzx
 * @description 客户端操作模板
 */
public class MessageClientTemplate {


    private static final Logger log = LoggerFactory.getLogger(MessageClientTemplate.class);

    private static final ExecutorService messageSendExecutor= Executors.newVirtualThreadPerTaskExecutor();


    /**
     * @Author fzx
     * @Description 同步发送消息
     */
    public static void syncSendMessage(Packet packet) {
        doSendMessage(packet, (sendResult)->{});
    }







    /**
     * @Author fzx
     * @Description 同步投递消息,不对外暴漏
     */
    private static void doSendMessage(Packet packet, SendCallback sendCallback) {
        // 从容器中获取
        // 通过packet 构造 ClientChannelPool, 作为key 获取ChannelPool, 获取某一个channel 去发送信息

        ChannelPoolKey clientChannelPool = new ChannelPoolKey(new ProtocolType(packet.getProtocol(), packet.getProtocolVersion()), "192.168.0.113:8083");
        SimpleChannelPool channelPool = MessageClientPool.clientSimpleChannelPoolMap.get(clientChannelPool);
        if (channelPool == null) {
            log.error("获取不到channelPool, 请检查是否已经调用了init方法");
            sendCallback.onCallback(SendResult.builder().packet(packet).exception(new MessageException("消息发送失败")).build());
            return;
        }
        Future<Channel> channelFuture =  channelPool.acquire();
        channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
            if (acquireFuture.isDone()) {
                // 判断是否连接成功
                if (acquireFuture.isSuccess()) {
                    Channel channel = acquireFuture.getNow();
                    // 客户端将数据写出到中介管道中
                    channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
                        if (channelFuture.isDone()) {
                            if (channelFuture.isSuccess()) {
                                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
                            }else {
                                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(future.cause()).build());
                            }
                        }
                    });;
                    // 用完后进行释放掉
                    channelPool.release(channel);
                } else {
                    Throwable cause = acquireFuture.cause();
                    System.out.println(cause.getMessage());
                    sendCallback.onCallback(SendResult.builder().packet(packet).exception(acquireFuture.cause()).build());
                }

            }
        });
    }

}
