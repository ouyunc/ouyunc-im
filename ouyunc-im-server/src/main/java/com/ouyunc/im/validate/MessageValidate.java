package com.ouyunc.im.validate;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.ImGroup;
import com.ouyunc.im.domain.ImUser;
import com.ouyunc.im.domain.bo.ImBlacklistBO;
import com.ouyunc.im.domain.bo.ImFriendBO;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
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
     *
     * @param identity 唯一标识（客户端或群组）
     * @param type     1-客户端，2-群组
     * @return
     */
    public static boolean isBanned(String appKey, String identity, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验identity {} 是否被平台封禁", identity);
        }
        // 校验客户端是否被平台封禁
        if (IMConstant.USER_TYPE_1.equals(type)) {
            ImUser imUser = DbHelper.getUser(appKey, identity);
            return imUser == null || IMConstant.USER_STATUS_1.equals(imUser.getStatus());
        }
        // 校验群组是否被平台封禁
        if (IMConstant.GROUP_TYPE_2.equals(type)) {
            ImGroup imGroup = DbHelper.getGroup(appKey, identity);
            return imGroup == null || IMConstant.GROUP_STATUS_1.equals(imGroup.getStatus());
        }
        return true;
    }

    /**
     * @param appKey
     * @param identity
     * @param loginDeviceType
     * @param ctx
     * @return boolean
     * @Author fangzhenxun
     * @Description 客户端是否认证（登录）
     */
    public static boolean isAuth(String appKey, String identity, byte loginDeviceType, ChannelHandlerContext ctx) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验identity {} 是否在设备: {} 登录认证", identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
        }
        //1,判断用户是否登录
        //2,判断用户权限是否授权
        // 组合成新的唯一标识
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, loginDeviceType);
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
        //3,从本地连接中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        // 判断是否合法
        if (loginUserInfo == null || bindCtx == null) {
            log.error("该客户端: {} 没有通过认证，现将其关闭", identity);
            // 没有登录以及出现异常后走的逻辑
            IMServerContext.LOGIN_USER_INFO_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
            IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
            // 关闭channel
            ctx.close();
            return false;
        }
        //3,从channel中的attrMap取出相关属性
        AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
        LoginUserInfo authenticationUserInfo = bindCtx.channel().attr(channelTagLoginKey).get();
        //4,判断是否已经登录，如果已经登录则提示已经登录，不做处理，如果未登录则走下面的登录逻辑
        if (authenticationUserInfo != null && ctx.channel().id().asLongText().equals(bindCtx.channel().id().asLongText())) {
            return true;
        }
        // 没有登录以及出现异常后走的逻辑
        IMServerContext.LOGIN_USER_INFO_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
        IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
        // 关闭channel
        ctx.close();
        bindCtx.close();
        log.error("该客户端channel id: {} 没有通过认证！", ctx.channel().id());
        return false;
    }


    /**
     * from是否有权限
     *
     * @param from        消息发送方唯一标识（客户端或群组）
     * @param type        1-客户端，2-群组
     * @param permissions 权限数组
     * @return
     */
    public static boolean hasPermission(String from, Integer type, String... permissions) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from {} 是否有 {} 权限", from, permissions);
        }
        return true;
    }

    /**
     * to 是否在from的好友列表中，否是好友关系
     *
     * @param from 消息发送方唯一标识（客户端）
     * @param to   消息接收方唯一标识（客户端）
     * @return
     */
    public static boolean isFriend(String appKey, String from, String to) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 和 to: {} 否是好友关系", from, to);
        }
        ImFriendBO imFriendBO = DbHelper.getFriend(appKey, from, to);
        return imFriendBO != null;
    }

    /**
     * from是否被to拉黑，是否被拉黑(在黑名单列表中)
     *
     * @param from 消息发送方唯一标识（客户端）
     * @param type 1-客户端，2-群组
     * @param to   消息接收方唯一标识（客户端或群组）
     * @return
     */
    public static boolean isBackList(String appKey, String from, String to, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否被 to: {} 拉黑(在黑名单列表中)", from, to);
        }
        ImBlacklistBO imBlacklistBO = DbHelper.getBackList(appKey, from, to, type);
        return imBlacklistBO != null;
    }

    /**
     * from是否被to屏蔽了，是否屏蔽（群组）/被屏蔽（客户端）
     *
     * @param from 消息发送方唯一标识
     * @param to   消息接收方唯一标识 (客户端或群组)
     * @param type 1-客户端，2-群组
     * @return
     */
    public static boolean isShield(String appKey, String from, String to, Integer type) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否被 to: {} 屏蔽(不接受它的消息)", from, to);
        }
        if (IMConstant.USER_TYPE_1.equals(type)) {
            ImFriendBO friend = DbHelper.getFriend(appKey, to, from);
            return friend == null || IMConstant.SHIELD.equals(friend.getFriendIsShield());
        }
        if (IMConstant.GROUP_TYPE_2.equals(type)) {
            ImGroupUserBO groupMember = DbHelper.getGroupMember(appKey, from, to);
            return groupMember == null || IMConstant.SHIELD.equals(groupMember.getIsShield());
        }
        return true;
    }


    /**
     * from 是否在群组groupIdentity中  是否已经在群中
     *
     * @param from          消息发送方唯一标识
     * @param groupIdentity 消息接收方(群)唯一标识
     * @return
     */
    public static boolean isGroup(String appKey, String from, String groupIdentity) {
        if (log.isDebugEnabled()) {
            log.debug("正在校验from: {} 是否加入群组 groupIdentity: {}", from, groupIdentity);
        }
        ImGroupUserBO groupMember = DbHelper.getGroupMember(appKey, from, groupIdentity);
        return groupMember != null;
    }

}
