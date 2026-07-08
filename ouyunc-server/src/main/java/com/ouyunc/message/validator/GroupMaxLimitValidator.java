package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 群的最大数限制，某个用户最大能拥有的最大群数限制，可以根据不同的appKey 来定制配置
 */
public enum GroupMaxLimitValidator implements Validator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(GroupMaxLimitValidator.class);



    /***
     * @author fzx
     * @description todo 校验发送者所属appKey 某个客户端所配置的最大群数量，以及当前所拥有的群数是否超过配置的群数量， 超过返回true,反之返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        return false;
    }
}
