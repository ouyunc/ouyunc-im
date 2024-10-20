package com.ouyunc.base.utils;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;

/**
 * channel属性工具类
 */
public class ChannelAttrUtil {

    /**
     * 设置channel的属性值
     */
    public static<T> void setChannelAttribute(ChannelHandlerContext ctx, String attrKey, T t) {
        setChannelAttribute(ctx.channel(), attrKey, t);
    }

    /**
     * 设置channel的属性值
     */
    public static<T> void setChannelAttribute(Channel channel, String attrKey, T t) {
        AttributeKey<T> channelTagLoginKey = AttributeKey.valueOf(attrKey);
        channel.attr(channelTagLoginKey).set(t);
    }

    /**
     * 获取channel的属性值
     */
    public static<T> T getChannelAttribute(ChannelHandlerContext ctx, String attrKey) {
        return getChannelAttribute(ctx.channel(), attrKey);
    }

    /**
     * 获取channel的属性值
     */
    public static<T> T getChannelAttribute(Channel channel, String attrKey) {
        AttributeKey<T> channelTagLoginKey = AttributeKey.valueOf(attrKey);
        return channel.attr(channelTagLoginKey).get();
    }
}
