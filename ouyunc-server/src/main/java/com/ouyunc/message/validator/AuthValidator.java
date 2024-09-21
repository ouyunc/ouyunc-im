package com.ouyunc.message.validator;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 认证验证器断言,单例
 */
public enum AuthValidator implements Validator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(AuthValidator.class);



    /***
     * @author fzx
     * @description 校验发送方是否登录，只在消息首次接收的服务器上做校验，集群传递消息不做权限验证
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        byte deviceTypeValue = packet.getDeviceType();
        Message message = packet.getMessage();
        String from = message.getFrom();
        String deviceTypeName = MessageServerContext.deviceTypeCache.get(deviceTypeValue).getDeviceTypeName();
        if (log.isDebugEnabled()) {
            log.debug("正在校验消息发送方 from {} 是否已在设备: {} 登录认证", from, deviceTypeName);
        }
        //1,判断用户是否登录, 2024-09-21 这里修改不从redis取登录信息，减少Redis的压力
        AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        LoginClientInfo loginClientInfo = ctx.channel().attr(channelTagLoginKey).get();
        // 判断是否在线， 也可以进行扩展进行给客户端发送登录认证失败的消息
        return loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus()) && MessageContext.messageProperties.getLocalServerAddress().equals(loginClientInfo.getLoginServerAddress());
    }
}
