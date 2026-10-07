package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.repository.cs.CsAgentType;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsImSessionRouteFields;
import com.ouyunc.repository.cs.CsImSessionRouteReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * IM 只读 CS 会话路由：主键 {@code ticketId}。
 */
public final class CsImSessionRouteSupport {

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
            // Redis 故障与路由不存在不是同一业务事实，保留异常供入口返回可重试结果。
            throw new IllegalStateException("客服会话路由读取失败，等待同一消息重试: ticketId=" + ticketId, e);
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
            String assignee = blankToNull(valueAt(values, 0));
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
                    snapshot.channelType(),
                    agentType,
                    epoch);
        } catch (Exception e) {
            // 路由刷新是关单和转接后的权限边界；读取失败时不能沿用旧坐席快照。
            throw new IllegalStateException("客服投递路由读取失败，等待同一消息重试: ticketId="
                    + snapshot.ticketId(), e);
        }
    }

    private static String valueAt(List<Object> values, int index) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return null;
        }
        return values.get(index).toString();
    }

    private static String blankToNull(String raw) {
        return StringUtils.isBlank(raw) ? null : raw;
    }
}
