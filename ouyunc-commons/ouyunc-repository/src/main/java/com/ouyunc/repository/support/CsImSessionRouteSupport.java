package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsImSessionRouteFields;
import com.ouyunc.repository.cs.CsImSessionRouteReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * IM 只读 CS 会话路由 Redis Hash（{@link CsImSessionRouteFields#READ_FIELDS}）。
 */
public final class CsImSessionRouteSupport {

    private static final Logger log = LoggerFactory.getLogger(CsImSessionRouteSupport.class);

    private final StringRedisTemplate stringRedisTemplate;

    public CsImSessionRouteSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public CsImSessionRoute getRoute(String appKey, String sessionId) {
        if (StringUtils.isAnyBlank(appKey, sessionId)) {
            return null;
        }
        try {
            String key = CacheConstant.buildCsSessionRouteCacheKey(appKey, sessionId);
            List<String> fields = CsImSessionRouteFields.READ_FIELDS;
            List<Object> values = stringRedisTemplate.opsForHash().multiGet(key, List.copyOf(fields));
            return CsImSessionRouteReader.read(fields, values);
        } catch (Exception e) {
            log.warn("读取客服会话路由 Hash 失败 appKey={} sessionId={}: {}", appKey, sessionId, e.getMessage());
            return null;
        }
    }
}
