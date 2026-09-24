package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.device.DeviceTypeRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 设备类型验证器断言,单例
 */
public enum DeviceValidator implements Validator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(DeviceValidator.class);



    /***
     * @author fzx
     * @description 校验通过返回true，否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String appKey = message.getMetadata().getIngress().getAppKey();
        // 软校验：白名单未命中返回 false，不抛异常
        return DeviceTypeRegistry.supports(appKey, from, packet.getDeviceType());
    }
}
