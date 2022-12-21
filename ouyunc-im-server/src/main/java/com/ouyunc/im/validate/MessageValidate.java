package com.ouyunc.im.validate;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息校验工具类
 */
public class MessageValidate {
    private static Logger log = LoggerFactory.getLogger(MessageValidate.class);

    /**
     * 是否被平台封禁
     * @param from  消息发送方唯一标识（客户端或群组）
     * @param type  1-客户端，2-群组
     * @return
     */
    public static boolean isBanned(String from, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from {} 是否被平台封禁", from);
        }
        return true;
    }

    /**
     * 客户端是否认证（登录）
     * @param identity  消息发送方唯一标识
     * @return
     */
    public static boolean isAuth(String identity, ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验identity {} 是否登录认证", identity);
        }
        //1,判断用户是否登录
        //2,判断用户权限是否授权
        // 组合成新的唯一标识
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, packet.getDeviceType());
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.get(CacheConstant.OUYUNC +   CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
        //3,从本地连接中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        // 判断是否合法
        if (loginUserInfo == null || bindCtx == null) {
            // 没有登录以及出现异常后走的逻辑
            IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.OUYUNC +   CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
            IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
            // 关闭channel
            ctx.close();
        }
        //3,从channel中的attrMap取出相关属性
        AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
        final LoginUserInfo authenticationUserInfo = bindCtx.channel().attr(channelTagLoginKey).get();
        //4,判断是否已经登录，如果已经登录则提示已经登录，不做处理，如果未登录则走下面的登录逻辑
        // @TODO 在这里后期如果有需要可以加上权限的校验，这里目前没有涉及到
        if (authenticationUserInfo != null && ctx.channel().id().asLongText().equals(bindCtx.channel().id().asLongText())) {
            return true;
        }
        log.error("该客户端channel id: {} 发送的packet: {} 没有通过认证！", ctx.channel().id(), packet);
        return false;
    }


    /**
     * 是否有权限
     * @param from  消息发送方唯一标识（客户端或群组）
     * @param type  1-客户端，2-群组
     * @param permissions  权限数组
     * @return
     */
    public static boolean hasPermission(String from, Integer type, String... permissions) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from {} 是否有 {} 权限", from, permissions);
        }
        return true;
    }

    /**
     * 否是好友关系
     * @param from  消息发送方唯一标识（客户端）
     * @param to  消息接收方唯一标识（客户端）
     * @return
     */
    public static boolean isFriend(String from, String to) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 和 to: {} 否是好友关系", from, to);
        }
        return true;
    }

    /**
     * 是否被拉黑(在黑名单列表中)
     * @param from  消息发送方唯一标识（客户端）
     * @param type  1-客户端，2-群组
     * @param to  消息接收方唯一标识（客户端或群组）
     * @return
     */
    public static boolean isBackList(String from,  String to, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否被 to: {} 拉黑(在黑名单列表中)", from, to);
        }
        return true;
    }

    /**
     * 是否屏蔽（群组）/被屏蔽（客户端）
     * @param from  消息发送方唯一标识
     * @param to  消息接收方唯一标识 (客户端或群组)
     * @param type  1-客户端，2-群组
     * @return
     */
    public static boolean isShield(String from, String to, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否被 to: {} 屏蔽(不接受它的消息)", from, to);
        }
        return true;
    }


    /**
     * 是否已经在群中
     * @param from  消息发送方唯一标识
     * @param groupIdentity  消息接收方(群)唯一标识
     * @return
     */
    public static boolean isGroup(String from, String groupIdentity) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否加入群组 groupIdentity: {}", from, groupIdentity);
        }
        return true;
    }

}
