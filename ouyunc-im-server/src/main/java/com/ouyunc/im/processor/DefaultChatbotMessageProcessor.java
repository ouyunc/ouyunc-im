package com.ouyunc.im.processor;

import com.ouyunc.im.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认机器人托管处理器
 */
public class DefaultChatbotMessageProcessor extends AbstractChatbotMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(DefaultChatbotMessageProcessor.class);

    /**
     * 处理的顺序
     *
     * @param
     * @return int
     */
    @Override
    public int order() {
        return 1;
    }

    /**
     * 不进行传递处理
     *
     * @param packet
     * @return
     */
    @Override
    public boolean match(Packet packet) {
        return false;
    }

    /**
     * 机器人托管处理器
     *
     * @param packet
     */
    @Override
    public void doProcess(Packet packet) {
        log.info("DefaultChatbotMessageProcessor do nothing");
        // do nothing
    }
}
