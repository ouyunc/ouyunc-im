package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

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
    public static void bind(ChannelHandlerContext ctx, LoginClientInfo loginClientInfo) {
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN ,loginClientInfo);
        // 将心跳设置到ctx 中
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT , loginClientInfo.getHeartBeatTimeout());
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP ,loginClientInfo.getLastLoginTime());
        // 存入本地用户注册表
        String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        MessageServerContext.localClientRegisterTable.put(comboIdentity, ctx);
        // 使用分布式锁来处理重复登录
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + comboIdentity);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 客户端登录信息存入缓存
                RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                        String key = CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity;
                        long loginExpireTime = loginClientInfo.getLoginExpireTime();
                        if (loginExpireTime <= 0) {
                            operations.opsForValue().set((K) key, (V) loginClientInfo);
                            // appKey 连接信息的score 如果是小于0 也就是 -1 则证明是不需要进行保活，一致保留到缓存中
                            operations.opsForZSet().add((K) (CacheConstant.OUYUNC + CacheConstant.CONNECTIONS + CacheConstant.APP_KEY + loginClientInfo.getAppKey()), (V) comboIdentity, NumberConstant.NUMBER_NEGATIVE_1);
                        }else {
                            operations.opsForValue().set((K) key, (V) loginClientInfo, loginExpireTime, TimeUnit.SECONDS);
                            // 添加appKey统计信息
                            operations.opsForZSet().add((K) (CacheConstant.OUYUNC + CacheConstant.CONNECTIONS + CacheConstant.APP_KEY + loginClientInfo.getAppKey()), (V) comboIdentity, TimeUtil.currentTimeMillis() + loginExpireTime*MessageConstant.NUMBER_1000);
                        }
                        return null;
                    }
                });

            }else {
                log.error("客户端: {} 绑定登录信息失败,原因：获取分布式锁失败", loginClientInfo);
            }
        } catch (Exception e) {
            log.error("客户端绑定登录信息失败,原因：{}", e.getMessage());
            throw new MessageException(e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /***
     * @author fzx
     * @description 获取最终客户端心跳时间
     */
    public static int calculateClientHeartBeatTimeout(int heartBeatExpireTime) {
        int heartBeatTimeSeconds = MessageServerContext.serverProperties().getClientHeartBeatTimeout();
        if (heartBeatExpireTime > NumberConstant.NUMBER_0) {
            int x = Math.round(heartBeatExpireTime * MessageConstant.ZERO_POINT_FIVE);
            heartBeatTimeSeconds = x >= NumberConstant.NUMBER_5 ? heartBeatExpireTime + NumberConstant.NUMBER_5 : heartBeatExpireTime + x;
        }
        return heartBeatTimeSeconds;
    }


    /**
     * 计算客户端登录过期时间
     * @param heartBeatExpireTime
     * @return
     */
    public static long calculateClientLoginExpireTime(int heartBeatExpireTime) {
        long expireTime = NumberConstant.NUMBER_NEGATIVE_1;
        // 计算心跳超时时间
        int heartBeatTimeout = calculateClientHeartBeatTimeout(heartBeatExpireTime);
        // 如果客户端的登录信息存储模式是有限/短暂的则 保存时间是，心跳间隔时间*最大重试次数+5，这里加5是为了尽可能给其他程序去处理相关逻辑，如读写空闲事件
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
            expireTime = Integer.toUnsignedLong((heartBeatTimeout * MessageServerContext.serverProperties().getClientHeartBeatWaitRetry())) + NumberConstant.NUMBER_5;
        }
        return expireTime;
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
        if (excludeDeviceTypeArr != null && excludeDeviceTypeArr.length > NumberConstant.NUMBER_0) {
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
        List<LoginClientInfo> loginClientInfoList = new ArrayList<>(NumberConstant.NUMBER_3);
        // 从ctx上下文获取客户端登录信息
        allLoginClientChannelHandlerContexts.forEach(ctx -> {
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
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
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
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
