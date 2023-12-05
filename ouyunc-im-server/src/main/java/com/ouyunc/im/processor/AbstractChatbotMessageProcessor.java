package com.ouyunc.im.processor;

import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 抽象机器人处理器
 */
public abstract class AbstractChatbotMessageProcessor implements MessageProcessor {

    private AbstractChatbotMessageProcessor next;

    public AbstractChatbotMessageProcessor setNextHandler(AbstractChatbotMessageProcessor next) {
        this.next = next;
        return this;
    }


    /**
     * 是否匹配，如果匹配往下个托管处理器传递
     *
     * @param packet
     * @return
     */
    public boolean match(Packet packet) {
        return true;
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        doProcess0(packet);
        // 判断是否要传递给下一个
        if (match(packet) && next != null) {
            next.doProcess(ctx, packet);
        }
    }

    /**
     * 执行顺序,越小优先级越高
     *
     * @return
     */
    public abstract int order();


    /**
     * 子类去实现具体业务逻辑
     *
     * @param packet
     */
    public abstract void doProcess0(Packet packet);
}
