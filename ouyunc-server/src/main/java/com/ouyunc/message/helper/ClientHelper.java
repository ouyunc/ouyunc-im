package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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
    public static LoginClientInfo bind(LoginContent loginContent, long loginTimestamp,ChannelHandlerContext ctx) {
        String appKey = loginContent.getAppKey();
        String identity = loginContent.getIdentity();
        DeviceType deviceType = loginContent.getDeviceType();
        log.info("channel: {} 正在绑定客户端: {} 登录设备: {} 在平台标识appKey: {} 上的登录",ctx.channel().id().asShortText(), identity, deviceType.getDeviceTypeName(), appKey);
        AttributeKey<LoginClientInfo> channelTagLoginKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        // 将用户绑定到channel中并打上tag标签
        LoginClientInfo loginClientInfo = new LoginClientInfo(MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null, loginTimestamp, appKey, identity, deviceType, loginContent.getSignature(), loginContent.getSignatureAlgorithm(), loginContent.getHeartBeatExpireTime(), loginContent.getEnableWill(), loginContent.getWillMessage(), loginContent.getWillTopic(), loginContent.getCleanSession(), loginContent.getSessionExpiryInterval(), loginContent.getCreateTime());
        ctx.channel().attr(channelTagLoginKey).set(loginClientInfo);
        // 存入本地用户注册表
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, deviceType.getDeviceTypeName());
        MessageServerContext.localClientRegisterTable.put(comboIdentity, ctx);
        // 使用分布式锁来处理重复登录
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + comboIdentity);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 客户端登录信息存入缓存
                MessageServerContext.remoteLoginClientInfoCache.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity , loginClientInfo);
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

}
