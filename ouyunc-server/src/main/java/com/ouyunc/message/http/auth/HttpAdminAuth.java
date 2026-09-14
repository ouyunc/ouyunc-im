package com.ouyunc.message.http.auth;

import com.ouyunc.base.constant.HttpAuthScopeConstant;
import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 节点运维 HTTP 鉴权：入口默认关闭；开启后必须使用独立运维 JWT（不得回退到推送密钥），
 * 并校验 {@link HttpAuthScopeConstant#IM_ADMIN_DRAIN}。
 */
public final class HttpAdminAuth {

    private static final Logger log = LoggerFactory.getLogger(HttpAdminAuth.class);

    private HttpAdminAuth() {
    }

    /**
     * drain / undrain / kick-clients：管理入口未开启视为不存在；开启后校验运维 JWT 与 drain 权限。
     */
    public static HttpAuthPrincipal requireDrain(HttpContext httpContext) throws HttpPipelineException {
        MessageServerProperties props = MessageServerContext.serverProperties();
        if (props == null || !props.isHttpAdminEnabled()) {
            throw HttpJwtAuth.notFound("管理入口未开启（ouyunc.message.http-admin.enabled=true）");
        }
        if (!HttpJwtAuth.hasValidSecret(props.getHttpAdminJwtSecret())) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "管理入口已开启但未配置独立运维 JWT 密钥 ouyunc.message.http-admin.jwt.secret");
        }
        if (StringUtils.equals(props.getHttpAdminJwtSecret(), props.getHttpPushJwtSecret())) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "运维 JWT 密钥不得与业务推送密钥相同");
        }
        HttpAuthPrincipal principal = HttpJwtAuth.parseBearer(httpContext, props.getHttpAdminJwtSecret(), false);
        if (!principal.hasScope(HttpAuthScopeConstant.IM_ADMIN_DRAIN)) {
            audit("admin-auth", principal, props.getLocalServerAddress(),
                    "", "deny", "missing scope " + HttpAuthScopeConstant.IM_ADMIN_DRAIN);
            throw HttpJwtAuth.forbidden("缺少节点运维权限（需要 scope: " + HttpAuthScopeConstant.IM_ADMIN_DRAIN + "）");
        }
        httpContext.setAuthPrincipal(principal);
        return principal;
    }

    /**
     * 关系缓存失效：始终要求业务推送 JWT（与运维入口开关无关）。
     * 本租户需要 {@link HttpAuthScopeConstant#IM_RELATION_CACHE}；跨租户还需要平台权限。
     */
    public static HttpAuthPrincipal requireRelationCache(HttpContext httpContext) throws HttpPipelineException {
        MessageServerProperties props = MessageServerContext.serverProperties();
        String secret = props != null ? props.getHttpPushJwtSecret() : null;
        if (!HttpJwtAuth.hasValidSecret(secret)) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "关系缓存失效需要配置 ouyunc.message.http-push.jwt.secret");
        }
        HttpAuthPrincipal principal = HttpJwtAuth.parseBearer(httpContext, secret, false);
        if (!principal.hasAnyScope(HttpAuthScopeConstant.IM_RELATION_CACHE, HttpAuthScopeConstant.IM_ADMIN_PLATFORM)) {
            throw HttpJwtAuth.forbidden("缺少关系缓存失效权限（需要 scope: "
                    + HttpAuthScopeConstant.IM_RELATION_CACHE + " 或 "
                    + HttpAuthScopeConstant.IM_ADMIN_PLATFORM + "）");
        }
        httpContext.setAuthPrincipal(principal);
        return principal;
    }

    /**
     * 租户凭证强制使用 Principal.appKey；仅平台权限可指定其他租户。
     *
     * @return 实际生效的 appKey
     */
    public static String bindTenantAppKey(HttpAuthPrincipal principal, String requestedAppKey)
            throws HttpPipelineException {
        if (principal == null || StringUtils.isBlank(principal.getAppKey())) {
            throw HttpJwtAuth.unauthorized("缺少鉴权主体 appKey");
        }
        String principalAppKey = principal.getAppKey();
        if (principal.hasScope(HttpAuthScopeConstant.IM_ADMIN_PLATFORM)) {
            return StringUtils.isNotBlank(requestedAppKey) ? requestedAppKey.trim() : principalAppKey;
        }
        if (StringUtils.isNotBlank(requestedAppKey) && !StringUtils.equals(requestedAppKey.trim(), principalAppKey)) {
            throw HttpJwtAuth.forbidden("无权指定其他租户 appKey");
        }
        return principalAppKey;
    }

    /**
     * 原因优先取 query {@code reason}，其次请求头 {@link HttpRequestConstant#HTTP_HEADER_ADMIN_REASON}。
     */
    public static String resolveReason(HttpContext httpContext, String queryReason) {
        String raw = StringUtils.isNotBlank(queryReason) ? queryReason : headerReason(httpContext);
        return sanitizeReason(raw);
    }

    public static void audit(String action, HttpAuthPrincipal principal, String node, String reason, String result,
                             String error) {
        String operator = principal != null ? principal.getIdentity() : "-";
        String appKey = principal != null ? principal.getAppKey() : "-";
        if (StringUtils.isNotBlank(error)) {
            log.warn("HTTP 运维操作 action={} operator={} appKey={} node={} reason={} result={} error={}",
                    action, operator, appKey, node, reason, result, error);
            return;
        }
        log.warn("HTTP 运维操作 action={} operator={} appKey={} node={} reason={} result={}",
                action, operator, appKey, node, reason, result);
    }

    private static String headerReason(HttpContext httpContext) {
        if (httpContext.getRequest() == null || httpContext.getRequest().headers() == null) {
            return null;
        }
        return httpContext.getRequest().headers().get(HttpRequestConstant.HTTP_HEADER_ADMIN_REASON);
    }

    private static String sanitizeReason(String reason) {
        if (StringUtils.isBlank(reason)) {
            return "";
        }
        String compact = reason.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() > HttpRequestConstant.HTTP_ADMIN_REASON_MAX_LENGTH) {
            return compact.substring(0, HttpRequestConstant.HTTP_ADMIN_REASON_MAX_LENGTH);
        }
        return compact;
    }
}
