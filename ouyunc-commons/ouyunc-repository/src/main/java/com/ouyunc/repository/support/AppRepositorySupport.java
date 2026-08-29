package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.domain.entity.AppEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 应用（appKey）查询：Redis Hash {@code ouyunc:app-keys} miss / 断连时回源 {@code ouyunc_im_app} 并回写。
 *
 * <p>使用场景：登录/HTTP 鉴权只读 Redis 时，云 Redis 重启或 failover 会把 Hash 打空，导致误报 appKey 不存在。
 * 坑：DB 查询失败时返回空列表，预热不得覆盖 Redis，避免把仍有效的缓存清掉。</p>
 */
public final class AppRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(AppRepositorySupport.class);

    private static final String PARAM_APP_KEY = "app_key";

    private final RepositoryInfrastructure infra;

    public AppRepositorySupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    /**
     * Redis 优先，miss 或 Redis 异常时查库；查到则尽力回写 Hash。
     */
    @SuppressWarnings("unchecked")
    public AppEntity getAppEntity(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        AppEntity app = getFromRedis(appKey);
        if (app != null) {
            return app;
        }
        app = getFromDatabase(appKey);
        if (app != null) {
            putToRedis(app);
        }
        return app;
    }

    /**
     * 启动预热：有 DB 结果才覆盖 Redis Hash。返回已加载的 appKey 列表（即使 Redis 写入失败也返回，供设备类型加载兜底）。
     */
    @SuppressWarnings("unchecked")
    public List<String> warmupAppKeys() {
        List<AppEntity> apps = listFromDatabase();
        if (apps.isEmpty()) {
            log.warn("预热 app-keys：数据库未返回应用，保留现有 Redis");
            return List.of();
        }
        Map<String, AppEntity> map = new LinkedHashMap<>();
        for (AppEntity app : apps) {
            if (app != null && StringUtils.isNotBlank(app.getAppKey())) {
                map.put(app.getAppKey(), app);
            }
        }
        if (map.isEmpty()) {
            log.warn("预热 app-keys：结果无有效 appKey，保留现有 Redis");
            return List.of();
        }
        String cacheKey = CacheConstant.buildAppKeysCacheKey();
        try {
            // 不先 DEL 再写入：避免 DEL 成功、putAll 失败把 Hash 打空
            infra.redisTemplate.opsForHash().putAll(cacheKey, map);
            log.info("预热 app-keys 完成, size={}", map.size());
        } catch (Exception e) {
            log.error("预热 app-keys 写入 Redis 失败, size={}", map.size(), e);
        }
        return new ArrayList<>(map.keySet());
    }

    @SuppressWarnings("unchecked")
    private AppEntity getFromRedis(String appKey) {
        try {
            return (AppEntity) infra.redisTemplate.opsForHash().get(CacheConstant.buildAppKeysCacheKey(), appKey);
        } catch (Exception e) {
            log.error("从 Redis 查询 appKey 异常, appKey={}", appKey, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void putToRedis(AppEntity app) {
        try {
            infra.redisTemplate.opsForHash().put(CacheConstant.buildAppKeysCacheKey(), app.getAppKey(), app);
        } catch (Exception e) {
            log.error("回写 Redis app-keys 失败, appKey={}", app.getAppKey(), e);
        }
    }

    private AppEntity getFromDatabase(String appKey) {
        try {
            return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectApp())
                    .param(PARAM_APP_KEY, appKey)
                    .query(AppEntity.class)
                    .optional()
                    .orElse(null);
        } catch (Exception e) {
            log.error("从数据库查询 appKey 异常, appKey={}", appKey, e);
            return null;
        }
    }

    private List<AppEntity> listFromDatabase() {
        try {
            return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectAllApps())
                    .query(AppEntity.class)
                    .list();
        } catch (Exception e) {
            log.error("从数据库加载全部 app 异常", e);
            return List.of();
        }
    }
}
