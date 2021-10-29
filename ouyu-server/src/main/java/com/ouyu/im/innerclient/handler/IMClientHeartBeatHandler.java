package com.ouyu.im.innerclient.handler;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author fangzhenxun
 * @Description: 内置客户端的channel处理器, 在这个处理类中主要做channel 通道的动态监听
 * 注册和激活：当客户端连接时，首先会触发注册，进行一些初始化的工作，然后激活连接，就可以收发消息了。
 * 断开和注销：当客户端断开时，反向操作，先断开，再注销。
 * 读取消息：当收到客户端消息时，首先读取，然后触发读取完成。
 * 发生异常：不多解释了。
 * 用户事件：由用户触发的各种非常规事件，根据evt的类型来判断不同的事件类型，从而进行不同的处理。
 * 可写状态变更：收到消息后，要回复消息，会先把回复内容写到缓冲区。而缓冲区大小是有一定限制的，当达到上限以后，可写状态就会变为否，不能再写。等缓冲区的内容被冲刷掉后，缓冲区又有了空间，可写状态又会变为是。
 * @Version V1.0
 **/
public class IMClientHeartBeatHandler extends ChannelInboundHandlerAdapter {
    private static Logger log = LoggerFactory.getLogger(IMClientHeartBeatHandler.class);

    // 锁
    private static final Lock lock = new ReentrantLock(true);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        ctx.fireChannelActive();
    }

    /**
     * @Author fangzhenxun
     * @Description 检测用户时间，用于动态对channel 的管理
     * @param ctx
     * @param event
     * @return void
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        Channel channel = ctx.channel();
        // 当该空闲事件触发时，则说明该通道channel没有任何的消息过来,则需要进行判断进行释放处理
        if (event instanceof IdleStateEvent) {
            // 判断该通道是否是存活
            if(channel.isActive()) {
                try {
                    // 从该channel中取出标签
                    AttributeKey<Integer> channelPoolHashCodeKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_POOL);
                    final Integer channelPoolHashCode = channel.attr(channelPoolHashCodeKey).get();
                    // 获取锁
                    lock.lock();
                    // 获取当前管道所属的channel pool 的hashcode
                    ConcurrentHashSet<Channel> coreChannelSet = IMServerContext.CLUSTER_CORE_CHANNEL_CACHE.get(channelPoolHashCode, key -> new ConcurrentHashSet<>(6));
                    // 判断当前核心coreChannelSet中是否已经满了，有可能这里的核心线程一个都没有，但是总的channel已经达到最大值,该channel 不正在写
                    if (coreChannelSet.stream().filter(ch -> ch.isActive()).count() >= IMServerContext.SERVER_CONFIG.getClusterChannelPoolCoreConnection()) {
                        // 直接关闭该通道，应该移除通道
                        log.error("===============我要关闭通道了：{}===================", channel.id().asShortText());
                        channel.close();
                        return;
                    }
                    // 先将当前存活的channel 存入集合中
                    coreChannelSet.add(channel);
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

}
