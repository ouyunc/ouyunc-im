package com.ouyunc.im.dispatcher;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.handler.MqttProtocolDispatcherHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: mqtt 协议
 **/
public class MqttDispatcherProcessorStrategy implements DispatcherProcessorStrategy {
    private static Logger log = LoggerFactory.getLogger(MqttDispatcherProcessorStrategy.class);

    /**
     * @param ctx
     * @return void
     * @Author fangzhenxun
     * @Description 在这里进行处理原生mqtt协议以及不同版本
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        log.info("mqtt 协议分发器 MqttDispatcherProcessorStrategy 正在处理...");
        ctx.pipeline()
                .addLast(IMConstant.HTTP_DISPATCHER_HANDLER, new MqttProtocolDispatcherHandler())
                .remove(IMConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }
}
