package com.ouyunc.im.innerclient.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
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
 **/
public class IMInnerClientHeartBeatHandler extends ChannelInboundHandlerAdapter {
    private static Logger log = LoggerFactory.getLogger(IMInnerClientHeartBeatHandler.class);

    // 锁
    private static final Lock lock = new ReentrantLock(true);



    /**
     * @Author fangzhenxun
     * @Description 检测用户时间，用于动态对channel的管理, 在触发响应的idle 规则后，会触发这里的事件方法，并作出判断和处理
     * 当核心channel处于空闲状态时会触发这里的事件
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
                    AttributeKey<Integer> channelPoolHashCodeKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_POOL);
                    final Integer channelPoolHashCode = channel.attr(channelPoolHashCodeKey).get();
                    // 获取锁
                    lock.lock();
                    // 获取当前管道所属的channel pool 的hashcode
                    Set<Channel> coreChannelSet = IMServerContext.CLUSTER_INNER_CLIENT_CORE_CHANNEL_POOL.get(channelPoolHashCode);
                    // 判断当前核心coreChannelSet中是否已经满了，有可能这里的核心线程一个都没有，但是总的channel已经达到最大值,该channel 不正在写
                    // 注意；核心channel添加的场景及规则如下：一开始消息很多会频繁的创建内部客户端channel直到达到最大channel(有最大channel数规则限制)，
                    // 当消息少量时，会触发该空闲事件，如果核心channel pool 没有达到设置的数量则，添加到池中，后面消息多的时候会优先从channel池中取出channel,
                    // 不在需要创建新的channel,除非消息很多，核心channel 池中的channel 已经用完，则会重新新建channel来处理大量消息
                    if (coreChannelSet.stream().filter(ch -> ch.isActive()).count() >= IMServerContext.SERVER_CONFIG.getClusterInnerClientChannelPoolCoreConnection()) {
                        // 直接关闭该通道，应该移除通道
                        log.warn("===============内部客户端核心channel已经满了，且现在channel {} 处于空闲状态，所以需要关闭该 channel===================", channel.id().asShortText());
                        // 这里的关闭会触发内部客户端的关闭，进行核心线程数的相关逻辑处理
                        channel.close();
                        return;
                    }
                    // 先将当前存活的channel 存入集合中，本地内存会自动变化
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
