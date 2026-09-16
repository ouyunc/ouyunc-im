package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.device.DeviceTypeRegistry;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author fzx
 * @description 设备类型验证器断言,单例
 */
public enum DeviceValidator implements Validator<Packet> {
    INSTANCE;

    /***
     * @author fzx
     * @description 校验通过返回true，否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String appKey = message.getMetadata().getAppKey();
        // 软校验：identity 定制白名单与 appKey/全局取交集，非法设备返回 false，不抛异�?
        return DeviceTypeRegistry.supports(appKey, from, packet.getDeviceType());
    }
}
