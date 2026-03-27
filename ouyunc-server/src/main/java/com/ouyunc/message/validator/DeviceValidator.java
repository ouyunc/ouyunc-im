package com.ouyunc.message.validator;

import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

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
        String appKey = message.getMetadata().getAppKey();
        return MessageServerContext.deviceTypeList(appKey, from).stream().map(DeviceType::getType).collect(Collectors.toSet()).contains(packet.getDeviceType());
    }
}
