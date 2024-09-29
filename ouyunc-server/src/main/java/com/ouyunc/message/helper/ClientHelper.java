package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author fzx
 * @description 客户端助手
 */
public class ClientHelper {

    private static final Logger log = LoggerFactory.getLogger(ClientHelper.class);


    /***
     * @author fzx
     * @description 客户端绑定登录信息
     */
    public static LoginClientInfo bind(ChannelHandlerContext ctx, LoginContent loginContent, long loginTimestamp) {
        String appKey = loginContent.getAppKey();
        String identity = loginContent.getIdentity();
        DeviceType deviceType = loginContent.getDeviceType();
        log.info("channel: {} 正在绑定客户端: {} 登录设备: {} 在平台标识appKey: {} 上的登录",ctx.channel().id().asShortText(), identity, deviceType.getDeviceTypeName(), appKey);
        AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        // 将用户绑定到channel中并打上tag标签
        LoginClientInfo loginClientInfo = new LoginClientInfo(MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null, loginTimestamp, appKey, identity, deviceType, loginContent.getSignature(), loginContent.getSignatureAlgorithm(), loginContent.getHeartBeatExpireTime(), loginContent.getEnableWill(), loginContent.getWillMessage(), loginContent.getWillTopic(), loginContent.getCleanSession(), loginContent.getSessionExpiryInterval(), loginContent.getCreateTime());
        ctx.channel().attr(channelTagLoginKey).set(loginClientInfo);
        // 存入本地用户注册表
        String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getAppKey(), identity, deviceType.getDeviceTypeName());
        MessageServerContext.localClientRegisterTable.put(comboIdentity, ctx);
        // 使用分布式锁来处理重复登录
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + comboIdentity);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                long expireTime = MessageConstant.MINUS_ONE;
                // 如果客户端的登录信息存储模式是有限/短暂的则 保存时间是，心跳间隔时间*最大重试次数+5，这里加5是为了尽可能给其他程序去处理相关逻辑，如读写空闲事件
                if (SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
                    int heartBeatExpire = loginContent.getHeartBeatExpireTime() > MessageConstant.ZERO ? Math.round(loginContent.getHeartBeatExpireTime() * MessageConstant.ONE_POINT_FIVE) : MessageServerContext.serverProperties().getClientHeartBeatTimeout();
                    expireTime = Integer.toUnsignedLong((heartBeatExpire * MessageServerContext.serverProperties().getClientHeartBeatWaitRetry())) + MessageConstant.FIVE;
                }
                // 客户端登录信息存入缓存
                MessageServerContext.remoteLoginClientInfoCache.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity, loginClientInfo, expireTime, TimeUnit.SECONDS);
            }else {
                log.error("客户端: {} 绑定登录信息失败,原因：获取分布式锁失败", loginContent);
            }
        } catch (Exception e) {
            log.error("客户端绑定登录信息失败,原因：{}", e.getMessage());
            throw new MessageException(e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return loginClientInfo;
    }


    /**
     * @param identity          用户登录唯一标识，手机号，邮箱，身份证号码等
     * @param excludeDeviceTypeArr 需要排除的设备类型数组
     * @return String
     * @Author fzx
     * @Description 判断客户端是否在线, 如果在线返回该客户端所有在线连接的登录信息，支持多端登录
     */
    public static List<LoginClientInfo> onlineAll(String appKey, String identity, DeviceType... excludeDeviceTypeArr) {
        List<LoginClientInfo> loginServerAddressList = new ArrayList<>();
        // 获取所有的实现DeviceType接口的枚举实例
        ConcurrentMap<Byte, DeviceType> deviceTypeCacheMap = MessageServerContext.deviceTypeCache.asMap();
        Stream<DeviceType> deviceTypeStream = deviceTypeCacheMap.values().parallelStream();
        if (excludeDeviceTypeArr != null && excludeDeviceTypeArr.length > 0) {
            deviceTypeStream = deviceTypeStream.filter(deviceType -> {
                boolean contain = false;
                for (DeviceType excludeDeviceType : excludeDeviceTypeArr) {
                    if (deviceType.getDeviceTypeName().equals(excludeDeviceType.getDeviceTypeName())) {
                        contain = true;
                    }
                }
                return contain;
            });
        }
        Set<String> comboIdentitySet = deviceTypeStream.map(deviceType -> IdentityUtil.generalComboIdentity(appKey, identity, deviceType)).collect(Collectors.toSet());
        int validComboIdentitySetSize = comboIdentitySet.size();
        // 先从本地注册表获取，如果在同一个服务器上或者不是集群
        Collection<ChannelHandlerContext> allLoginClientChannelHandlerContexts = MessageServerContext.localClientRegisterTable.getAll(comboIdentitySet);
        // 判断comboIdentitySet的size 与结果集的大小是否相等，如果不相等则在从redis获取，如果相等则返回
        AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        List<LoginClientInfo> loginClientInfoList = new ArrayList<>(3);
        // 从ctx上下文获取客户端登录信息
        allLoginClientChannelHandlerContexts.forEach(ctx -> {
            LoginClientInfo loginClientInfo = ctx.channel().attr(channelTagLoginKey).get();
            if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
                loginClientInfoList.add(loginClientInfo);
                // 移除掉已经从本地获取的有效客户端登录信息
                comboIdentitySet.remove(IdentityUtil.generalComboIdentity(appKey, identity, loginClientInfo.getDeviceType()));
            }
        });
        if (validComboIdentitySetSize == loginClientInfoList.size()) {
            return loginClientInfoList;
        }
        // 如果不相等，则将没有查询到的数据通过缓存来获取
        // comboIdentitySet 中排除已经从本地获取结果的key
        Collection<LoginClientInfo> remoteCacheLoginClientInfos = MessageServerContext.remoteLoginClientInfoCache.getAll(comboIdentitySet);
        if (CollectionUtils.isNotEmpty(remoteCacheLoginClientInfos)) {
             // 筛选合法的数据
            loginServerAddressList.addAll(remoteCacheLoginClientInfos.stream().filter(loginClientInfo -> loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())).collect(Collectors.toList()));
        }
        // 最后返回符合条件的数据
        return loginServerAddressList;
    }

    /**
     * 获取某个端的登录信息
     * @param identity 客户端唯一标识
     * @param loginDeviceType 客户端登录的设备类型 不为空
     * @return
     */
    public static LoginClientInfo online(String appKey, String identity, DeviceType loginDeviceType) {
       return online(appKey, identity, loginDeviceType.getDeviceTypeName());
    }
    /**
     * 获取某个端的登录信息,不暴露该接口
     * @param identity 客户端唯一标识
     * @param loginDeviceTypeName 客户端登录的设备类型名称
     * @return
     */
    private static LoginClientInfo online(String appKey, String identity, String loginDeviceTypeName) {
        String comboIdentity = IdentityUtil.generalComboIdentity(appKey, identity, loginDeviceTypeName);
        // 先从本地注册表获取，如果在同一个服务器上或者不是集群
        ChannelHandlerContext ctx = MessageServerContext.localClientRegisterTable.get(comboIdentity);
        if (ctx != null) {
            AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            LoginClientInfo loginClientInfo = ctx.channel().attr(channelTagLoginKey).get();
            if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus()) && MessageContext.messageProperties.getLocalServerAddress().equals(loginClientInfo.getLoginServerAddress())) {
                return loginClientInfo;
            }
        }
        // 从redis 获取登录信息
        LoginClientInfo loginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity);
        if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
            return loginClientInfo;
        }
        return null;
    }
}
