package com.ouyunc.message.processor;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * @Author fzx
 * @Description: 消息抽象处理类
 **/
public abstract class AbstractMessageProcessor<T extends Number> extends AbstractBaseProcessor<T> {
    private static final Logger log = LoggerFactory.getLogger(AbstractMessageProcessor.class);


    /**
     * 线程池事件执行器
     */
    public static final ExecutorService messageProcessorExecutor =  Executors.newVirtualThreadPerTaskExecutor();


    /**
     * @Author fzx
     * @Description 前置处理器，做认证授权相关处理，在真正处理消息前处理
     */
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        messageProcessorExecutor.execute(() -> {
            repository().save(packet);
        });
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
            ctx.close();
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }


    /**
     * @Author fzx
     * @Description 传递处理器，仅做了一层包装，交给下个处理器去处理
     */
    protected void fireProcess(ChannelHandlerContext ctx, Packet packet, BiConsumer<ChannelHandlerContext, Packet> function) {
        function.accept(ctx, packet);
        // 交给下个处理器去处理
        ctx.fireChannelRead(packet);
    }

    /**
     * @Author fzx
     * @Description 做后置处理
     */
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        ctx.fireChannelRead(packet);
    }
}
