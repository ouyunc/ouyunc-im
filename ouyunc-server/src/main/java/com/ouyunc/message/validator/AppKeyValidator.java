package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.base.constant.enums.AppStatus;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.helper.ClientHelper;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author fzx
 * @description appKey验证器断言,单例
 */
public enum AppKeyValidator implements Validator<String> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(AppKeyValidator.class);

    private static final RedisTemplate<String, String> redisTemplate = CacheFactory.REDIS.instance();


    /***
     * @author fzx
     * @description 校验appKey是否合法, 返回true -合法， 返回false-不合法
     *
     * <p>性能优化：使用 opsForHash().get() 精确查询单个 appKey，
     * 而非 entries() 全量拉取所有 appKey（避免 O(N) Redis 传输 + 内存开销）。</p>
     */
    @Override
    public boolean verify(String appKey, ChannelHandlerContext ctx) {
        AppEntity app = redisTemplate.<String, AppEntity>opsForHash().get(CacheConstant.buildAppKeysCacheKey(), appKey);
        if (app == null) {
            log.warn("appKey:{}不存在", appKey);
            return false;
        }
        if (AppStatus.ABNORMAL.value().equals(app.getStatus())) {
            log.warn("appKey:{}已停用", appKey);
            return false;
        }
        Long maxConnections = app.getMaxConnections();
        // maxConnections == null 视为无限制（兼容未配置场景）
        if (maxConnections == null || maxConnections == NumberConstant.NUMBER_NEGATIVE_1) {
            return true;
        }
        long currentConnections = ClientHelper.connections(appKey);
        if (currentConnections < maxConnections) {
            return true;
        }
        log.warn("appKey:{}连接数已达上限, 当前:{}, 上限:{}", appKey, currentConnections, maxConnections);
        return false;
    }
}
