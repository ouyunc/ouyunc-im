package com.ouyunc.im.processor;

import com.ouyunc.im.packet.Packet;

/**
 * 抽象机器人处理器
 */
public abstract class AbstractChatbotMessageProcessor implements ChatbotMessageProcessor {

    private ChatbotMessageProcessor next;

    public AbstractChatbotMessageProcessor setNextHandler(ChatbotMessageProcessor next) {
        this.next = next;
        return this;
    }

    @Override
    public void process(Packet packet) {
        doProcess(packet);
        // 判断是否要传递给下一个
        if (match(packet) && next != null) {
            next.process(packet);
        }
    }

    /**
     * 执行顺序,越小优先级越高
     * @return
     */
    public abstract int order();


    /**
     * 子类去实现具体业务逻辑
     * @param packet
     */
    public abstract void doProcess(Packet packet);
}
