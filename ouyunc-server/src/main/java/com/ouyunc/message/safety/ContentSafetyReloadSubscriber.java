package com.ouyunc.message.safety;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 订阅 Redis 内容安全热更新频道（Redisson Topic）。
 */
public final class ContentSafetyReloadSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyReloadSubscriber.class);

    private static volatile Integer listenerId;

    private ContentSafetyReloadSubscriber() {
    }

    public static synchronized void start(Consumer<String> onReload) {
        if (listenerId != null) {
            return;
        }
        try {
            RTopic topic = MessageServerContext.redissonClient.getTopic(CacheConstant.CONTENT_SAFETY_RELOAD_CHANNEL);
            listenerId = topic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String msg) {
                    String payload = StringUtils.defaultIfBlank(msg, "ALL");
                    log.info("收到内容安全热更新通知 channel={} payload={}", channel, payload);
                    onReload.accept(payload);
                }
            });
            log.info("内容安全热更新订阅已启动 channel={}", CacheConstant.CONTENT_SAFETY_RELOAD_CHANNEL);
        } catch (Exception e) {
            log.error("内容安全热更新订阅失败，将仅依赖本地缓存过期刷新", e);
        }
    }
}
