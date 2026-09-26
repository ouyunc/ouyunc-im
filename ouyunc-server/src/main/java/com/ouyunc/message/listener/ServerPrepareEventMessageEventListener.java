package com.ouyunc.message.listener;

import com.google.common.collect.Lists;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.core.engine.ReactiveRedisLuaScriptEngine;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.cluster.AppKeyDeviceTypeSubscriber;
import com.ouyunc.core.relation.RelationCacheSubscriber;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.safety.ContentSafetyRegistry;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.List;
import java.util.Set;

/**
 * Netty bind 前准备：须在对外端口打开前完成的预热与 Redis 订阅。
 * <p>由 {@code beforeInitServer} 同步发布 {@link MessageEventTypeEnum#SERVER_PREPARE} 触发。</p>
 */
@EventListener
class ServerPrepareEventMessageEventListener implements MessageEventListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(ServerPrepareEventMessageEventListener.class);

    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_PREPARE;
    }

    @Override
    public void onEvent(MessageEvent event) {
        // appKey / 设备类型：先预热本地表，再挂订阅（须在 bind 前，避免首登空窗）
        warmupAppKeyDeviceTypes();
        AppKeyDeviceTypeSubscriber.start();
        // 内容安全：订阅 Redis 热更新（须在 Netty bind 前）
        ContentSafetyRegistry.getInstance().start();
        // 关系本机缓存：订阅 Redis 失效频道（业务写 Redis 后 PUBLISH）
        RelationCacheSubscriber.start();
        // 预加载 Lua 脚本 SHA 到本地（集群模式下注意各节点同步）
        preloadLuaScripts();
        log.info("服务初始化前准备完成：appKey 设备类型/内容安全/关系缓存订阅已启动，Lua 已预加载");
    }

    /**
     * 预热 appKey 与设备类型到本机缓存。
     */
    private void warmupAppKeyDeviceTypes() {
        List<String> warmedAppKeys;
        try {
            // 先从 ouyunc_im_app 预热 Redis Hash，避免 Redis 空缓存时登录全部报 appKey 不存在
            warmedAppKeys = DefaultRepository.INSTANCE.warmupAppKeys();
        } catch (Exception e) {
            log.error("预热 app-keys 失败", e);
            warmedAppKeys = List.of();
        }
        Set<String> appKeys;
        try {
            appKeys = ClientHelper.appKeys();
        } catch (Exception e) {
            log.error("启动读取 Redis app-keys 失败，使用预热列表兜底", e);
            appKeys = Set.of();
        }
        if (CollectionUtils.isEmpty(appKeys) && CollectionUtils.isNotEmpty(warmedAppKeys)) {
            appKeys = Set.copyOf(warmedAppKeys);
        }
        if (CollectionUtils.isNotEmpty(appKeys)) {
            loadAppKeyDeviceTypes(Lists.newArrayList(appKeys));
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAppKeyDeviceTypes(List<String> appKeys) {
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (String appKey : appKeys) {
                    operations.opsForSet().members((K) CacheConstant.buildAppKeyDeviceTypeCacheKey(appKey));
                }
                return null;
            }
        });
        for (int i = 0; i < appKeys.size(); i++) {
            String appKey = appKeys.get(i);
            Set<Byte> deviceTypeSet = (Set<Byte>) results.get(i);
            if (CollectionUtils.isNotEmpty(deviceTypeSet)) {
                DeviceTypeRegistry.replaceAppKeyWhitelist(appKey, deviceTypeSet);
            }
        }
    }

    /**
     * 将 Lua 脚本 SHA 缓存到本地。
     */
    private void preloadLuaScripts() {
        for (LuaScriptEnum luaScript : LuaScriptEnum.values()) {
            log.debug("预加载lua脚本: {}, {}", luaScript.name(), luaScript.getScript());
            ReactiveRedisLuaScriptEngine.preloadLuaScript(luaScript);
        }
    }
}
