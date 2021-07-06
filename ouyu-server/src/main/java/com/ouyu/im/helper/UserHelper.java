package com.ouyu.im.helper;

import cn.hutool.json.JSONUtil;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.DeviceEnum;
import com.ouyu.im.constant.enums.LoginEnum;
import com.ouyu.im.constant.enums.OnlineEnum;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.entity.ChannelUserInfo;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.exception.IMException;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.AcknowledgeMessage;
import com.ouyu.im.packet.message.ResponseMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.ouyu.im.constant.enums.MessageEnum.IM_ACKNOWLEDGE;


/**
 * @Author fangzhenxun
 * @Description: 用户的绑定，以及其他处理
 * @Version V1.0
 **/
public class UserHelper {
    private static Logger log = LoggerFactory.getLogger(UserHelper.class);


    /**
     * @Author fangzhenxun
     * @Description 绑定用户
     * @param identity
     * @param ctx
     * @return void
     */
    public static void bind(String identity, ChannelHandlerContext ctx) {
        AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
        ChannelUserInfo authenticationUserInfo = new ChannelUserInfo(LoginEnum.LOGIN_STATUS_SIGNED_IN);
        authenticationUserInfo.setIdentity(identity);
        ctx.channel().attr(channelTagLoginKey).set(authenticationUserInfo);
        LoginUserInfo loginUserInfo = new LoginUserInfo(IMContext.LOCAL_ADDRESS, OnlineEnum.ONLINE);
        loginUserInfo.setIdentity(identity);
        IMContext.LOGIN_USER_INFO_CACHE.put(identity, loginUserInfo);
        IMContext.LOCAL_USER_CHANNEL_CACHE.put(identity, ctx);
    }

    /**
     * @Author fangzhenxun
     * @Description 用户解绑
     * @param identity
     * @return void
     */
    public static void unbind(String identity) {
        IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
        IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
    }

    /**
     * @Author fangzhenxun
     * @Description 定时如果在一定时间内没有收到接收方返回的信息则重试发送信息（可能会导致重复接收，客户端需作去重处理）
     * @param from
     * @param to
     * @param packet
     * @return void
     */
    public static void doAck(String from, String to,  Packet packet) {
        if (IMContext.SERVER_CONFIG.isAcknowledgeModeEnable()) {
            // 3, 回执ack(AcknowledgeMessage)给发送方；
            try {
                AcknowledgeMessage acknowledgeMessage = new AcknowledgeMessage(to, from, Long.toString(packet.getPacketId()));
                Packet<AcknowledgeMessage> ackPacket  = new Packet(packet.getProtocol(), packet.getProtocolVersion(), packet.getPacketId(), DeviceEnum.PC_LINUX.getValue(), InetAddress.getLocalHost().getHostAddress(), IM_ACKNOWLEDGE.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(),  acknowledgeMessage);
                MessageHelper.sendMessage(ackPacket, from.split(ImConstant.COMMA));
            } catch (UnknownHostException e) {
                log.error("服务端回执消息ack失败！请查明原因");
                e.printStackTrace();
                throw new IMException("服务端回执消息ack失败！请查明原因");
            }
        }
    }



    /**
     * @Author fangzhenxun
     * @Description 认证信息
     * @param identity
     * @param ctx
     * @param packet
     * @return void
     */
    public static void doAuthentication(String identity, ChannelHandlerContext ctx, Packet packet) {
        // @todo ======================这里需要提取公共方法==================================
        // 否则判断是否登录并且有权限
        //8.1,判断该用户在分布式缓存中是否存在，不存在则判断该用户是否在本地缓存中存在，存在则关闭该通道,不存在则不做处理，结束
        //1,从分布式缓存取出该用户 @todo 需要处理优化缓存的数据
        Object obj = IMContext.LOGIN_USER_INFO_CACHE.get(identity);
        LoginUserInfo loginUserInfo = null;
        if (obj != null) {
            loginUserInfo = JSONUtil.toBean(JSONUtil.toJsonStr(obj), LoginUserInfo.class);
        }      //2,从本地连接中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMContext.LOCAL_USER_CHANNEL_CACHE.get(identity);

        if (loginUserInfo != null && bindCtx != null) {
            // 如果都不为空，接着继续判断
            //3,从channel中的attrMap取出相关属性
            AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
            final ChannelUserInfo authenticationUserInfo = bindCtx.channel().attr(channelTagLoginKey).get();
            //4,判断是否已经登录，如果已经登录则提示已经登录，不做处理，如果未登录则走下面的登录逻辑
            if (authenticationUserInfo != null && ctx.channel().id().asLongText().equals(bindCtx.channel().id().asLongText())) {
                // 已经登录，交给下个处理器去处理
                ctx.fireChannelRead(packet);
                return;
            }
        }
        // 没有登录以及出现异常后走的逻辑 @todo 后去优化逻辑
        IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
        IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
        //6,该通道没有绑定任何信息
        packet.setMessage(ResponseMessage.fail("该通道异常！现将关闭该通道"));
        ctx.writeAndFlush(packet);
        // 关闭channel
        ctx.close();
    }

}
