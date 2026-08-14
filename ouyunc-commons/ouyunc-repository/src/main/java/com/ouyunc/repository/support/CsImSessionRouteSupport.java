package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.repository.cs.CsAgentType;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsImSessionRouteFields;
import com.ouyunc.repository.cs.CsImSessionRouteReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * IM 只读 CS 会话路由：主键 {@code ticketId}。
 */
public final class CsImSessionRouteSupport {

    private static final Logger log = LoggerFactory.getLogger(CsImSessionRouteSupport.class);

    private final StringRedisTemplate stringRedisTemplate;

    public CsImSessionRouteSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /** @param ticketId 咨询单 ID（与消息 correlationId 一致） */
    public CsImSessionRoute getRoute(String appKey, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ticketId)) {
            return null;
        }
        try {
            String key = CacheConstant.buildCsSessionRouteCacheKey(appKey, ticketId.trim());
            List<String> fields = CsImSessionRouteFields.READ_FIELDS;
            List<Object> values = stringRedisTemplate.opsForHash().multiGet(key, List.copyOf(fields));
            return CsImSessionRouteReader.read(fields, values);
        } catch (Exception e) {
            log.warn("读取客服会话路由 Hash 失败 appKey={} ticketId={}: {}", appKey, ticketId, e.getMessage());
            return null;
        }
    }

    /**
     * 投递前二次读取 assignee/epoch/status（绕过任何本地缓存）。
     * 转接与关单发生在 prepare 快照之后时，以本结果覆盖投递目标。
     *
     * @return 路由已删除时返回 null
     */
    public CsImSessionRoute mergeLiveDelivery(String appKey, CsImSessionRoute snapshot) {
        if (snapshot == null || StringUtils.isAnyBlank(appKey, snapshot.ticketId())) {
            return snapshot;
        }
        try {
            String key = CacheConstant.buildCsSessionRouteCacheKey(appKey, snapshot.ticketId().trim());
            List<String> fields = CsImSessionRouteFields.DELIVERY_FIELDS;
            List<Object> values = stringRedisTemplate.opsForHash().multiGet(key, List.copyOf(fields));
            if (values == null || values.stream().allMatch(v -> v == null)) {
                return null;
            }
            String assignee = valueAt(values, 0);
            Long epoch = CsImSessionRouteReader.parseLong(valueAt(values, 1));
            Integer agentType = CsImSessionRouteReader.parseInt(valueAt(values, 2));
            Integer status = CsImSessionRouteReader.parseInt(valueAt(values, 3));
            if (StringUtils.isBlank(assignee)
                    || epoch == null
                    || epoch < 1L
                    || !CsAgentType.isKnown(agentType)
                    || status == null) {
                return null;
            }
            return new CsImSessionRoute(
                    snapshot.ticketId(),
                    snapshot.sessionId(),
                    snapshot.userId(),
                    snapshot.serviceIdentity(),
                    assignee,
                    status,
                    snapshot.channel(),
                    agentType,
                    epoch);
        } catch (Exception e) {
            log.warn("读取客服路由投递热字段失败 appKey={} ticketId={}: {}",
                    appKey, snapshot.ticketId(), e.getMessage());
            return snapshot;
        }
    }

    private static String valueAt(List<Object> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return null;
        }
        return values.get(index).toString();
    }
}
