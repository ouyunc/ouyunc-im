package com.ouyunc.message.processor.http.push;

import com.google.common.collect.Sets;
import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;
import com.ouyunc.message.properties.MessageServerProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
     * HTTP 推送 JWT 鉴权：从 {@code Authorization: Bearer} 解析发送方 identity/fromType，并按 pushType 校验 scope。
 */
public final class HttpPushJwtAuth {

    private static final Logger log = LoggerFactory.getLogger(HttpPushJwtAuth.class);

    static final String SCOPE_ADMIN = "im:push:admin";
    static final String SCOPE_PUSH = "im:push";
    static final String SCOPE_SYSTEM = "im:push:system";
    static final String SCOPE_ONE2ONE = "im:push:one2one";
    static final String SCOPE_GROUP = "im:push:group";
    static final String SCOPE_CS = "im:push:cs";

    private HttpPushJwtAuth() {
    }

    public static void authenticate(HttpContext httpContext, MessagePushRequest request) throws HttpPipelineException {
        MessageServerProperties props = MessageServerContext.serverProperties();
        if (!props.isHttpPushJwtEnabled()) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "HTTP 推送需要开启 JWT 鉴权（ouyunc.message.http-push.jwt.enabled=true）");
        }
        if (StringUtils.isBlank(props.getHttpPushJwtSecret())) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "HTTP 推送 JWT 已开启但未配置 ouyunc.message.http-push.jwt.secret");
        }
        String token = extractBearerToken(httpContext);
        if (StringUtils.isBlank(token)) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "缺少 JWT（请在请求头设置 " + HttpRequestConstant.HTTP_HEADER_AUTHORIZATION + ": Bearer <token>）");
        }
        HttpAuthPrincipal principal = parsePrincipal(token, httpContext.getAppKey(), props);
        validatePushScope(principal, request.getPushType());
        httpContext.setAuthPrincipal(principal);
    }

    private static HttpAuthPrincipal parsePrincipal(String token, String headerAppKey, MessageServerProperties props)
            throws HttpPipelineException {
        try {
            SecretKey key = Keys.hmacShaKeyFor(props.getHttpPushJwtSecret().getBytes(StandardCharsets.UTF_8));
            var parser = Jwts.parser().verifyWith(key);
            if (StringUtils.isNotBlank(props.getHttpPushJwtIssuer())) {
                parser.requireIssuer(props.getHttpPushJwtIssuer());
            }
            Claims claims = parser.build().parseSignedClaims(token).getPayload();

            String appKeyClaim = props.getHttpPushJwtAppKeyClaim();
            String tokenAppKey = claims.get(appKeyClaim, String.class);
            if (StringUtils.isBlank(tokenAppKey)) {
                throw unauthorized("JWT 缺少 appKey claim（" + appKeyClaim + "）");
            }
            if (!StringUtils.equals(tokenAppKey, headerAppKey)) {
                log.warn("JWT appKey 与请求头不一致: header={}, claim={}", headerAppKey, tokenAppKey);
                throw unauthorized("JWT appKey 与请求头 X-App-Key 不一致");
            }

            String identityClaim = props.getHttpPushJwtIdentityClaim();
            String identity = claims.get(identityClaim, String.class);
            if (StringUtils.isBlank(identity)) {
                throw unauthorized("JWT 缺少身份 claim（" + identityClaim + "）");
            }

            String fromTypeClaim = props.getHttpPushJwtFromTypeClaim();
            Integer fromType = claims.get(fromTypeClaim, Integer.class);
            if (fromType == null) {
                throw unauthorized("JWT 缺少 fromType claim（" + fromTypeClaim + "）");
            }
            if (MessageFromToTypeEnum.valueOf(fromType) == null) {
                throw unauthorized("JWT fromType 无效: " + fromType);
            }
            Set<String> scopes = parseScopes(claims, props.getHttpPushJwtScopeClaim());
            return new HttpAuthPrincipal(tokenAppKey, identity.trim(), fromType, scopes);
        } catch (ExpiredJwtException ex) {
            throw unauthorized("JWT 已过期");
        } catch (JwtException ex) {
            log.warn("JWT 校验失败: {}", ex.getMessage());
            throw unauthorized("JWT 无效: " + ex.getMessage());
        }
    }

    private static void validatePushScope(HttpAuthPrincipal principal, Integer pushTypeCode)
            throws HttpPipelineException {
        if (principal.hasScope(SCOPE_ADMIN)) {
            return;
        }
        PushTypeEnum pushType = pushTypeCode != null ? PushTypeEnum.getPushTypeEnum(pushTypeCode) : null;
        if (pushType == null) {
            if (!principal.hasScope(SCOPE_PUSH)) {
                throw forbidden("JWT 缺少推送权限（需要 scope: " + SCOPE_PUSH + " 或具体 pushType 对应 scope）");
            }
            return;
        }
        if (!hasScopeForPushType(principal.getScopes(), pushType)) {
            throw forbidden("JWT 缺少 pushType=" + pushTypeCode + " 对应推送权限");
        }
    }

    static boolean hasScopeForPushType(Set<String> scopes, PushTypeEnum pushType) {
        if (scopes.contains(SCOPE_ADMIN)) {
            return true;
        }
        return switch (pushType) {
            case SERVER_NOTIFY_TEXT, BROADCAST_SERVER_NOTIFY -> scopes.contains(SCOPE_SYSTEM);
            case ONE2ONE_TEXT -> scopes.contains(SCOPE_ONE2ONE) || scopes.contains(SCOPE_PUSH);
            case GROUP_TEXT -> scopes.contains(SCOPE_GROUP) || scopes.contains(SCOPE_PUSH);
            case CUSTOMER_SERVICE_TEXT -> scopes.contains(SCOPE_CS) || scopes.contains(SCOPE_PUSH);
        };
    }

    private static Set<String> parseScopes(Claims claims, String scopeClaim) {
        Object raw = claims.get(scopeClaim);
        if (raw == null) {
            return Sets.newHashSet();
        }
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toUnmodifiableSet());
        }
        String text = String.valueOf(raw).trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        if (StringUtils.isBlank(text)) {
            return Sets.newHashSet();
        }
        Set<String> scopes = new HashSet<>();
        if (text.contains(",")) {
            Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(scopes::add);
        } else {
            Arrays.stream(text.split("\\s+"))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(scopes::add);
        }
        return Set.copyOf(scopes);
    }

    private static String extractBearerToken(HttpContext httpContext) {
        if (httpContext.getRequest() == null || httpContext.getRequest().headers() == null) {
            return null;
        }
        String authorization = httpContext.getRequest().headers().get(HttpRequestConstant.HTTP_HEADER_AUTHORIZATION);
        if (StringUtils.isBlank(authorization)) {
            return null;
        }
        String prefix = "Bearer ";
        if (authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorization.substring(prefix.length()).trim();
        }
        return authorization.trim();
    }

    private static HttpPipelineException unauthorized(String message) {
        return new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED, message);
    }

    private static HttpPipelineException forbidden(String message) {
        return new HttpPipelineException(HttpResponseStatus.FORBIDDEN, HttpResponseCodeEnum.FORBIDDEN, message);
    }
}
