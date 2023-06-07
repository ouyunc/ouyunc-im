package com.ouyunc.im.helper;


import cn.hutool.core.date.SystemClock;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.*;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
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
 * @Version V3.0
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
        IMServerContext.LOGIN_USER_INFO_CACHE.putHashIfAbsent(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + identity, DeviceEnum.getDeviceNameByValue(loginDeviceType), loginUserInfo);
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
     * @Description 客户端做等待ack的队列处理 ，如果在一定时间内没有收到接收方返回的信息则重试发送信息（可能会导致重复接收，客户端需作去重处理）
     * @param from 消息接收方,不会转发发到多登录设备上
     * @param packet 原始消息packet
     * @return void
     */
    public static void doReplyAck(String from, Packet packet) {
        log.info("服务端正在回复from: {} ackPacket: {}", from, packet);
        // 异步直接发送
        MessageHelper.sendMessage(new Packet(packet.getProtocol(), packet.getProtocolVersion(), packet.getPacketId(), DeviceEnum.PC_OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getLocalHost(), MessageEnum.IM_REPLY_ACK.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(),  new Message(IMServerContext.SERVER_CONFIG.getLocalServerAddress(), from, MessageContentEnum.SERVER_REPLY_ACK_CONTENT.type(), JSONUtil.toJsonStr(packet), SystemClock.now())), IdentityUtil.generalComboIdentity(from, packet.getDeviceType()));
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
