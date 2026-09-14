package com.ouyunc.message.http.auth;

import com.google.common.collect.Sets;
import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
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
 * HTTP JWT 解析：从 {@code Authorization: Bearer} 取出身份与 scope。
 * 推送、运维、关系缓存失效共用解析逻辑，密钥与是否要求 fromType 由调用方决定。
 */
public final class HttpJwtAuth {

    private static final Logger log = LoggerFactory.getLogger(HttpJwtAuth.class);

    private HttpJwtAuth() {
    }

    /**
     * 校验密钥长度后解析 Bearer JWT，写入前不改 {@link HttpContext}。
     *
     * @param secret           HS256 密钥，须 ≥ {@link HttpRequestConstant#HTTP_JWT_SECRET_MIN_LENGTH}
     * @param requireFromType  推送场景必须带 fromType；运维凭证可省略
     */
    public static HttpAuthPrincipal parseBearer(HttpContext httpContext, String secret, boolean requireFromType)
            throws HttpPipelineException {
        if (!hasValidSecret(secret)) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "JWT 密钥未配置或长度不足 " + HttpRequestConstant.HTTP_JWT_SECRET_MIN_LENGTH + " 字符");
        }
        String token = extractBearerToken(httpContext);
        if (StringUtils.isBlank(token)) {
            throw unauthorized("缺少 JWT（请在请求头设置 " + HttpRequestConstant.HTTP_HEADER_AUTHORIZATION + ": Bearer <token>）");
        }
        return parsePrincipal(token, httpContext.getAppKey(), MessageServerContext.serverProperties(), secret, requireFromType);
    }

    public static boolean hasValidSecret(String secret) {
        return StringUtils.isNotBlank(secret)
                && secret.length() >= HttpRequestConstant.HTTP_JWT_SECRET_MIN_LENGTH;
    }

    public static String extractBearerToken(HttpContext httpContext) {
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

    private static HttpAuthPrincipal parsePrincipal(String token, String headerAppKey, MessageServerProperties props,
                                                    String secret, boolean requireFromType) throws HttpPipelineException {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            var parser = Jwts.parser().verifyWith(key);
            if (props != null && StringUtils.isNotBlank(props.getHttpPushJwtIssuer())) {
                parser.requireIssuer(props.getHttpPushJwtIssuer());
            }
            Claims claims = parser.build().parseSignedClaims(token).getPayload();

            String appKeyClaim = props != null ? props.getHttpPushJwtAppKeyClaim() : "appKey";
            String tokenAppKey = claims.get(appKeyClaim, String.class);
            if (StringUtils.isBlank(tokenAppKey)) {
                throw unauthorized("JWT 缺少 appKey claim（" + appKeyClaim + "）");
            }
            if (!StringUtils.equals(tokenAppKey, headerAppKey)) {
                log.warn("JWT appKey 与请求头不一致: header={}, claim={}", headerAppKey, tokenAppKey);
                throw unauthorized("JWT appKey 与请求头 X-App-Key 不一致");
            }

            String identityClaim = props != null ? props.getHttpPushJwtIdentityClaim() : "sub";
            String identity = claims.get(identityClaim, String.class);
            if (StringUtils.isBlank(identity)) {
                throw unauthorized("JWT 缺少身份 claim（" + identityClaim + "）");
            }

            String fromTypeClaim = props != null ? props.getHttpPushJwtFromTypeClaim() : "fromType";
            Integer fromType = claims.get(fromTypeClaim, Integer.class);
            if (requireFromType) {
                if (fromType == null) {
                    throw unauthorized("JWT 缺少 fromType claim（" + fromTypeClaim + "）");
                }
                if (MessageFromToTypeEnum.valueOf(fromType) == null) {
                    throw unauthorized("JWT fromType 无效: " + fromType);
                }
            } else if (fromType != null && MessageFromToTypeEnum.valueOf(fromType) == null) {
                throw unauthorized("JWT fromType 无效: " + fromType);
            }

            String scopeClaim = props != null ? props.getHttpPushJwtScopeClaim() : "scope";
            Set<String> scopes = parseScopes(claims, scopeClaim);
            return new HttpAuthPrincipal(tokenAppKey, identity.trim(), fromType, scopes);
        } catch (ExpiredJwtException ex) {
            throw unauthorized("JWT 已过期");
        } catch (HttpPipelineException ex) {
            throw ex;
        } catch (JwtException ex) {
            log.warn("JWT 校验失败: {}", ex.getMessage());
            throw unauthorized("JWT 无效: " + ex.getMessage());
        }
    }

    static Set<String> parseScopes(Claims claims, String scopeClaim) {
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

    public static HttpPipelineException unauthorized(String message) {
        return new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED, message);
    }

    public static HttpPipelineException forbidden(String message) {
        return new HttpPipelineException(HttpResponseStatus.FORBIDDEN, HttpResponseCodeEnum.FORBIDDEN, message);
    }

    public static HttpPipelineException notFound(String message) {
        return new HttpPipelineException(HttpResponseStatus.NOT_FOUND, HttpResponseCodeEnum.NOT_FOUND, message);
    }

    public static HttpPipelineException badRequest(String message) {
        return new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, message);
    }
}
