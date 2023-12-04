package com.ouyunc.im.processor;

import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息处理器接口
 **/
public interface MessageProcessor {
    Logger log = LoggerFactory.getLogger(MessageProcessor.class);



    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 前置处理器，做认证授权相关处理，在真正处理消息前处理
     */
    default void preProcess(ChannelHandlerContext ctx, Packet packet) {}


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做逻辑处理
     */
    void doProcess(ChannelHandlerContext ctx, Packet packet);



    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做后逻辑处理
     */
    default void postProcess(ChannelHandlerContext ctx, Packet packet) {
    }



}
