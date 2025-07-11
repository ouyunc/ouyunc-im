package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 最大群成员数量校验
 */
public enum GroupUserMaxLimitValidator implements Validator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(GroupUserMaxLimitValidator.class);



    /***
     * @author fzx
     * @description todo 校验appKey 某个客户端的群成员数量是否超过最大限制， 超过返回true, 否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        return false;
    }
}
