package com.ouyunc.message.context;

import com.ouyunc.message.helper.ClientHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护需要做连接数 ZSet 过期清理的 appKey 集合：启动时从 Redis 全量加载，运行期通过登录绑定与 appKey 设备类型广播增量加入，
 * 并按配置周期与 Redis 中的 appKey 注册表对账合并，避免仅依赖启动快照导致新 appKey 不参与清理。
 */
public final class AppKeyConnectionCleanupRegistry {

    private static final Set<String> TRACKED = ConcurrentHashMap.newKeySet();

    private AppKeyConnectionCleanupRegistry() {
    }

    public static void initFromRedis() {
        mergeAllFromRedis();
    }

    public static void track(String appKey) {
        if (StringUtils.isNotBlank(appKey)) {
            TRACKED.add(appKey);
        }
    }

    /**
     * 与 {@link ClientHelper#appKeys()} 对齐并合并，用于低频对账（不删除本地已跟踪项，避免与并发登录竞态）。
     */
    public static void mergeAllFromRedis() {
        Set<String> keys = ClientHelper.appKeys();
        if (keys != null && !keys.isEmpty()) {
            TRACKED.addAll(keys);
        }
    }

    public static void eachTracked(java.util.function.Consumer<String> action) {
        TRACKED.forEach(action);
    }

    public static boolean isEmpty() {
        return TRACKED.isEmpty();
    }
}
