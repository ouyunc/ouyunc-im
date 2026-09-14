package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.message.cluster.RelationCacheInvalidateSupport;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.http.auth.HttpAdminAuth;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;
import com.ouyunc.message.http.auth.HttpJwtAuth;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务侧 Redis 写完后 HTTP 通知本节点清关系 Caffeine，再集群同步。
 * 租户 JWT 强制使用 Principal.appKey；只有平台权限可指定其他租户。
 */
@HttpRestController
@HttpRequestMapping
public class RelationCacheInvalidateController {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidateController.class);

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_RELATION_CACHE_INVALIDATE_PATH)
    public HttpResponseResult<Void> invalidate(@RequestBody RelationCacheInvalidateEvent event,
                                               HttpContext httpContext) throws HttpPipelineException {
        if (event == null) {
            throw HttpJwtAuth.badRequest("请求体不能为空");
        }
        HttpAuthPrincipal principal = HttpAdminAuth.requireRelationCache(httpContext);
        String requestedAppKey = event.getAppKey();
        event.setAppKey(HttpAdminAuth.bindTenantAppKey(principal, requestedAppKey));
        String invalid = RelationCacheInvalidateSupport.validateForHttp(event);
        if (invalid != null) {
            throw HttpJwtAuth.badRequest(invalid);
        }
        if (!StringUtils.equals(principal.getAppKey(), event.getAppKey())) {
            log.warn("关系缓存失效跨租户 operator={} fromAppKey={} targetAppKey={} kind={}",
                    principal.getIdentity(), principal.getAppKey(), event.getAppKey(), event.getKind());
        }
        RelationCacheInvalidateSupport.applyLocalAndFanout(event);
        return HttpResponseResult.success();
    }
}
