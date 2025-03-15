package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author fzx
 * @description 权限校验器
 */
public enum PermissionValidator implements Validator<Packet> {

    INSTANCE;
    /***
     * @author fzx
     * @description 校验权限(可以校验 该用户所属的appKey 是否开启该消息类型，或者内容类型，或者该用户是否支持该消息类型和内容类型), 校验通过返回true, 校验失败返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        return true;
    }
}
