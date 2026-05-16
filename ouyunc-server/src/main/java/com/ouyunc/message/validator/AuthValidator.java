package com.ouyunc.message.validator;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.context.MessageContext;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
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
     * @description 校验连接已登录，并将 {@link Message#getFrom()} 绑定为 Channel 登录身份（不信任客户端 from）。
     * 只在消息首次接收的服务器上做校验，集群传递消息不做权限验证。
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        byte deviceTypeValue = packet.getDeviceType();
        Message message = packet.getMessage();
        String clientFrom = message.getFrom();
        LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (loginClientInfo == null
                || !OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())
                || !MessageContext.messageProperties.getLocalServerAddress().equals(loginClientInfo.getLoginServerAddress())) {
            return false;
        }
        String verifiedSender = loginClientInfo.getIdentity();
        if (StringUtils.isBlank(verifiedSender)) {
            log.warn("登录态 identity 为空，拒绝消息，deviceType={}", deviceTypeValue);
            return false;
        }
        if (StringUtils.isNotBlank(clientFrom) && !verifiedSender.equals(clientFrom)) {
            log.warn("消息 from 与登录身份不一致，拒绝。clientFrom={}, verifiedSender={}, deviceType={}",
                    clientFrom, verifiedSender, deviceTypeValue);
            return false;
        }
        message.setFrom(verifiedSender);
        if (log.isDebugEnabled()) {
            log.debug("发送方已绑定为登录用户 {}，deviceType={}", verifiedSender, deviceTypeValue);
        }
        return true;
    }
}
