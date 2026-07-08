package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 服务端通知消息处理器（MessageType=SERVER_NOTIFY）。
 * 供 HTTP 推送及内部系统通知复用；不经 {@code preProcess} 的 HTTP 入口会直接调用 {@link #process}。
 */
public final class ServerNotifyMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {

    private static final Logger log = LoggerFactory.getLogger(ServerNotifyMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.SERVER_NOTIFY;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            log.warn("SERVER_NOTIFY 缺少 message/metadata: {}", packet);
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        if (isBroadcast(packet)) {
            deliverBroadcast(appKey, packet);
            return;
        }
        List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isEmpty(targets)) {
            log.debug("SERVER_NOTIFY 接收方 {} 不在线", message.getTo());
            return;
        }
        MessageHelper.asyncSendMessage(packet, targets);
    }

    private static boolean isBroadcast(Packet packet) {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || metadata.getHttpPushType() == null) {
            return false;
        }
        PushTypeEnum pushType = PushTypeEnum.getPushTypeEnum(metadata.getHttpPushType());
        return pushType == PushTypeEnum.BROADCAST_SERVER_NOTIFY
                || MessageConstant.SPLAT.equals(packet.getMessage().getTo());
    }

    private static void deliverBroadcast(String appKey, Packet packet) {
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String connectionsKey = CacheConstant.buildConnectionsCacheKey(appKey);
        long now = TimeUtil.currentTimeMillis();
        Set<Object> comboIdentities = redisTemplate.opsForZSet().rangeByScore(connectionsKey, now, Double.POSITIVE_INFINITY);
        if (CollectionUtils.isEmpty(comboIdentities)) {
            log.debug("SERVER_NOTIFY 广播：appKey={} 无在线连接", appKey);
            return;
        }
        List<LoginClientInfo> allTargets = new ArrayList<>();
        for (Object comboIdentityObj : comboIdentities) {
            if (!(comboIdentityObj instanceof String comboIdentity) || StringUtils.isBlank(comboIdentity)) {
                continue;
            }
            String loginKey = CacheConstant.buildLoginCacheKey(appKey, comboIdentity);
            LoginClientInfo loginClientInfo = (LoginClientInfo) redisTemplate.opsForValue().get(loginKey);
            if (loginClientInfo != null) {
                allTargets.add(loginClientInfo);
            }
        }
        if (CollectionUtils.isEmpty(allTargets)) {
            log.debug("SERVER_NOTIFY 广播：appKey={} 未解析到在线 LoginClientInfo", appKey);
            return;
        }
        MessageHelper.asyncSendMessage(packet, allTargets);
    }
}
