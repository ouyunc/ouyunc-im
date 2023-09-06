package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.utils.SnowflakeUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.MDC;

/**
 * 日志全局链路记录
 */
public class IMLoggingHandler extends LoggingHandler{

    public IMLoggingHandler(LogLevel level) {
        super(level);
    }

    /**
     * 添加日志跟踪ID
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MDC.put(IMConstant.LOG_SPAN_ID, String.valueOf(SnowflakeUtil.nextId()));
        super.channelRead(ctx, msg);
    }
}
