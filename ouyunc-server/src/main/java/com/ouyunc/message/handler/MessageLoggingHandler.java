package com.ouyunc.message.handler;

import io.netty.handler.logging.ByteBufFormat;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 管道 I/O 日志：级别来自 {@code ouyunc.message.log.level}。
 * <p>
 * {@link LogLevel#DEBUG}/{@link LogLevel#TRACE} 时使用 {@link ByteBufFormat#HEX_DUMP}（完整 hex，流量大时极占日志）；
 * 更高级别使用 {@link ByteBufFormat#SIMPLE}，仅记录如 {@code READ: 65536B}，不打印缓冲区内容。
 */
public class MessageLoggingHandler extends LoggingHandler {

    public MessageLoggingHandler(LogLevel level) {
        super(level, byteBufFormat(level));
    }

    private static ByteBufFormat byteBufFormat(LogLevel level) {
        return level == LogLevel.TRACE || level == LogLevel.DEBUG
                ? ByteBufFormat.HEX_DUMP
                : ByteBufFormat.SIMPLE;
    }
}
