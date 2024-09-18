package com.ouyunc.message.handler;

import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * @author fzx
 * @description 消息日志处理器
 */
public class MessageLoggingHandler extends LoggingHandler {
    public MessageLoggingHandler(LogLevel level) {
        super(level);
    }
}
