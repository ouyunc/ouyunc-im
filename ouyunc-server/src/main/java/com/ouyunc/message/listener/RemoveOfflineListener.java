package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.RemoveOfflineEvent;
import com.ouyunc.message.context.MessageServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 异常离线消息监听器（所有qos > 0 的消息都应该进入每个客户端的离线队列中（待确认队列中），包括群聊和私聊模式的业务，这里将离线队列当做待确认队列使用）
 * 会话消息会实时保存全量聊天数据。离线消息的存在只是在某种业务上，单聊的会话消息不对外开放，客户端只能通过离线消息和实时获取的消息来展现；
 * 然而针对群组类的业务，考虑到服务器的压力以及多种做法，采用拉取模式来根据需要获取群组消息，定时获取群组消息，或者服务端通知客户端有群组消息，让客户端按需拉取群组消息，减少服务端压力；
 * 当然拉取的服务可以是其他业务服务器，这样减少了IM 服务器端的压力；
 * 注意：待接收确认消息，归并到离线消息中，所以收到qos的消息确认接收事件，就会从离线消息中删除已确认接收的数据；
 * 离线消息使用redis 的zset 数据结构来存储，目前只存储单聊的消息（推模式），对于群组类的业务（一般使用拉取模式，按需拉取）数据不进行存储，
 * 离线消息一般存储是有时间限制的，比如存储在离线消息的过期时间是7天，所以要启动一个定时任务定时去删除过期的离线消息
 */
public class RemoveOfflineListener implements MessageListener<RemoveOfflineEvent> {

    private static final Logger log = LoggerFactory.getLogger(RemoveOfflineListener.class);

    private final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    @Override
    public void onApplicationEvent(RemoveOfflineEvent event) {
        Object source = event.getSource();
        log.debug("移除离线消息监听器正在处理：{}", event.getSource());
        if (source instanceof Packet packet) {
            Message message = packet.getMessage();
            String from = message.getFrom();
            Metadata metadata = message.getMetadata();
            DeviceType deviceType = MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType());
            stringRedisTemplate.opsForZSet().remove(CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), from, deviceType.getDeviceTypeName()), SnowflakeUtil.formatLong(message.getContent()));
        }
    }

}
