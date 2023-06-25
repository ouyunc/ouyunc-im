package com.ouyunc.im.helper;


import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.*;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @Author fangzhenxun
 * @Description: 用户的绑定，以及其他处理
 **/
public class UserHelper {
    private static Logger log = LoggerFactory.getLogger(UserHelper.class);


    /**
     * @Author fangzhenxun
     * @Description 绑定用户
     * @param ctx
     * @param appKey 登录appKey
     * @param identity
     * @param loginDeviceType
     * @return void
     */
    public static void bind(String appKey, String identity, byte loginDeviceType, ChannelHandlerContext ctx) {
        log.info("正在绑定登录用户: {} 在设备: {} 上登录", identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, loginDeviceType);
        AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
        // 将用户绑定到channel中并打上tag标签
        LoginUserInfo loginUserInfo = new LoginUserInfo(appKey, identity, IMServerContext.SERVER_CONFIG.getLocalServerAddress(), OnlineEnum.ONLINE, DeviceEnum.getDeviceEnumByValue(loginDeviceType));
        ctx.channel().attr(channelTagLoginKey).set(loginUserInfo);
        // 存入用户登录信息
        IMServerContext.LOGIN_USER_INFO_CACHE.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType), loginUserInfo);
        // 存入本地用户注册表
        IMServerContext.USER_REGISTER_TABLE.put(comboIdentity, ctx);
        // 记录IM App 下的连接信息，多个设备，多个连接，后期有需求在改造
        IMServerContext.LOGIN_IM_APP_CONNECTIONS_CACHE.putHashIfAbsent(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.APP + appKey + CacheConstant.CONNECTION, comboIdentity, loginUserInfo);
    }

    /**
     * @Author fangzhenxun
     * @Description 用户解绑
     * @param identity
     * @param loginDeviceType
     * @param ctx
     * @return void
     */
    public static void unbind(String identity, byte loginDeviceType, ChannelHandlerContext ctx) {
        log.info("正在解绑客户端: {} 在设备: {} 上的用户: {}",ctx.channel().id().asShortText(),  DeviceEnum.getDeviceNameByValue(loginDeviceType), identity);
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, loginDeviceType);
        ChannelHandlerContext ctx0 = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        // 下面的close 会触发DefaultSocketChannelInitializer 中的close 监听事件，做删除缓存处理
        if (ctx != null) {
            ctx.close();
        }
        if (ctx0 != null) {
            ctx0.close();
        }
    }





    /**
     * @Author fangzhenxun
     * @Description 判断用户是否在线,如果在线返回所有在线连接的服务器地址，支持多端登录
     * @param identity 用户登录唯一标识，手机号，邮箱，身份证号码等
     * @return String
     */
    public static List<LoginUserInfo> onlineAll(String identity) {
        return onlineAll(identity, null);
    }

    /**
     * @Author fangzhenxun
     * @Description 判断用户是否在线,如果在线返回所有在线连接的服务器地址，支持多端登录
     * @param identity 用户登录唯一标识，手机号，邮箱，身份证号码等
     * @param excludeDeviceType 需要排除的设备类型
     * @return String
     */
    public static List<LoginUserInfo> onlineAll(String identity, Byte excludeDeviceType) {
        List<LoginUserInfo> loginServerAddressList = new ArrayList<>();
        Map<String, LoginUserInfo> loginUserInfoMap = IMServerContext.LOGIN_USER_INFO_CACHE.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + identity);
        if (MapUtil.isNotEmpty(loginUserInfoMap)) {
            loginUserInfoMap.forEach((loginDeviceName, loginUserInfo)->{
                if (excludeDeviceType == null || !loginDeviceName.equals(DeviceEnum.getDeviceNameByValue(excludeDeviceType))) {
                    if (loginUserInfo != null && OnlineEnum.ONLINE.equals(loginUserInfo.getOnlineStatus())) {
                        loginServerAddressList.add(loginUserInfo);
                    }
                }
            });
        }
        return loginServerAddressList;
    }

    /**
     * 获取某个端的登录信息
     * @param identity 客户端唯一标识
     * @param loginDeviceType 客户端登录的设备类型
     * @return
     */
    public static LoginUserInfo online(String identity, byte loginDeviceType) {
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType));
        if (loginUserInfo != null && OnlineEnum.ONLINE.equals(loginUserInfo.getOnlineStatus())) {
            return loginUserInfo;
        }
        return null;
    }
}
