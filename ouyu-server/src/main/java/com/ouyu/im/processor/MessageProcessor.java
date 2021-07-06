package com.ouyu.im.processor;

import com.ouyu.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fangzhenxun
 * @Description: 消息处理类
 * @Version V1.0
 **/
public interface MessageProcessor {


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做认证授权相关处理，在真正处理消息前处理
     */
    void preProcess(ChannelHandlerContext ctx, Packet packet);


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
    void postProcess(ChannelHandlerContext ctx, Packet packet);

}
