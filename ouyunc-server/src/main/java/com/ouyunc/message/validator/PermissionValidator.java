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
     * @description 校验权限
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        return true;
    }
}
