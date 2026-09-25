package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.ClientInfoRefresh;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订阅客户端信息变更频道。业务把 {@code ClientInfo} 写入 Redis 后发布，本机删掉正式缓存和未命中标记。
 */
public final class ClientInfoSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ClientInfoSubscriber.class);

    private static volatile Integer listenerId;

    private ClientInfoSubscriber() {
    }

    /**
     * 幂等启动订阅；重复调用直接返回。
     */
    public static synchronized void start() {
        if (listenerId != null) {
            return;
        }
        try {
            RTopic topic = MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_INFO_PUBLISH_TOPIC);
            listenerId = topic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String msg) {
                    onClientInfoMessage(msg);
                }
            });
            log.info("客户端信息缓存失效订阅已启动 channel={}", MessageConstant.CLIENT_INFO_PUBLISH_TOPIC);
        } catch (Exception e) {
            log.error("客户端信息缓存失效订阅失败，将仅依赖未命中标记过期", e);
        }
    }

    /**
     * 停止订阅；在服务停机时调用。
     */
    public static synchronized void stop() {
        Integer id = listenerId;
        listenerId = null;
        if (id == null) {
            return;
        }
        try {
            MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_INFO_PUBLISH_TOPIC)
                    .removeListener(id);
        } catch (Exception e) {
            log.warn("停止客户端信息缓存失效订阅异常: {}", e.getMessage());
        }
    }

    private static void onClientInfoMessage(String msg) {
        if (StringUtils.isBlank(msg)) {
            return;
        }
        try {
            ClientInfoRefresh refresh = JSON.parseObject(msg, ClientInfoRefresh.class);
            if (refresh == null || StringUtils.isAnyBlank(refresh.getAppKey(), refresh.getIdentity())) {
                return;
            }
            MessageServerContext.evictLocalClientInfo(refresh.getAppKey(), refresh.getIdentity());
            log.info("已失效本地客户端信息 appKey={} identity={}", refresh.getAppKey(), refresh.getIdentity());
        } catch (Exception e) {
            log.warn("客户端信息缓存失效消息处理失败 payload={}", msg, e);
        }
    }
}
