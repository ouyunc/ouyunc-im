package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.HttpAuthScopeConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;
import com.ouyunc.message.http.auth.HttpJwtAuth;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Set;

/**
 * HTTP 推送 JWT 鉴权：从 {@code Authorization: Bearer} 解析发送方 identity/fromType，并按 pushType 校验 scope。
 */
public final class HttpPushJwtAuth {

    private HttpPushJwtAuth() {
    }

    public static void authenticate(HttpContext httpContext, MessagePushRequest request) throws HttpPipelineException {
        MessageServerProperties props = MessageServerContext.serverProperties();
        if (!props.isHttpPushJwtEnabled()) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "HTTP 推送需要开启 JWT 鉴权（ouyunc.message.http-push.jwt.enabled=true）");
        }
        if (!HttpJwtAuth.hasValidSecret(props.getHttpPushJwtSecret())) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "HTTP 推送 JWT 已开启但未配置 ouyunc.message.http-push.jwt.secret");
        }
        HttpAuthPrincipal principal = HttpJwtAuth.parseBearer(httpContext, props.getHttpPushJwtSecret(), true);
        validatePushScope(principal, request.getPushType());
        httpContext.setAuthPrincipal(principal);
    }

    /**
     * Packet 构建后二次校验：未显式传 pushType 时按实际 messageType 校验 scope，
     * 避免 SYSTEM/BOT 省略 pushType 仅凭 {@code im:push} 绕过 {@code im:push:system}。
     */
    public static void validateResolvedPacketScope(HttpContext httpContext, MessagePushRequest request, Packet packet)
            throws HttpPipelineException {
        if (request.getPushType() != null) {
            return;
        }
        HttpAuthPrincipal principal = httpContext.getAuthPrincipal();
        if (principal == null || principal.hasScope(HttpAuthScopeConstant.IM_PUSH_ADMIN)) {
            return;
        }
        byte messageType = packet.getMessageType();
        if (messageType == MessageTypeEnum.SERVER_NOTIFY.getType()) {
            if (!principal.hasScope(HttpAuthScopeConstant.IM_PUSH_SYSTEM)) {
                throw forbidden("JWT 缺少系统通知推送权限（需要 scope: " + HttpAuthScopeConstant.IM_PUSH_SYSTEM + "）");
            }
            return;
        }
        if (messageType == MessageTypeEnum.ONE_2_ONE.getType()) {
            if (!principal.hasScope(HttpAuthScopeConstant.IM_PUSH_ONE2ONE)
                    && !principal.hasScope(HttpAuthScopeConstant.IM_PUSH)) {
                throw forbidden("JWT 缺少单聊推送权限");
            }
            return;
        }
        if (messageType == MessageTypeEnum.GROUP.getType()) {
            if (!principal.hasScope(HttpAuthScopeConstant.IM_PUSH_GROUP)
                    && !principal.hasScope(HttpAuthScopeConstant.IM_PUSH)) {
                throw forbidden("JWT 缺少群聊推送权限");
            }
            return;
        }
        if (messageType == MessageTypeEnum.CUSTOMER_SERVICE.getType()) {
            if (!principal.hasScope(HttpAuthScopeConstant.IM_PUSH_CS)
                    && !principal.hasScope(HttpAuthScopeConstant.IM_PUSH)) {
                throw forbidden("JWT 缺少客服推送权限");
            }
        }
    }

    private static void validatePushScope(HttpAuthPrincipal principal, Integer pushTypeCode)
            throws HttpPipelineException {
        if (principal.hasScope(HttpAuthScopeConstant.IM_PUSH_ADMIN)) {
            return;
        }
        PushTypeEnum pushType = pushTypeCode != null ? PushTypeEnum.getPushTypeEnum(pushTypeCode) : null;
        if (pushType == null) {
            if (!principal.hasScope(HttpAuthScopeConstant.IM_PUSH)) {
                throw forbidden("JWT 缺少推送权限（需要 scope: " + HttpAuthScopeConstant.IM_PUSH + " 或具体 pushType 对应 scope）");
            }
            return;
        }
        if (!hasScopeForPushType(principal.getScopes(), pushType)) {
            throw forbidden("JWT 缺少 pushType=" + pushTypeCode + " 对应推送权限");
        }
    }

    static boolean hasScopeForPushType(Set<String> scopes, PushTypeEnum pushType) {
        if (scopes.contains(HttpAuthScopeConstant.IM_PUSH_ADMIN)) {
            return true;
        }
        return switch (pushType) {
            case SERVER_NOTIFY_TEXT, BROADCAST_SERVER_NOTIFY -> scopes.contains(HttpAuthScopeConstant.IM_PUSH_SYSTEM);
            case ONE2ONE_TEXT -> scopes.contains(HttpAuthScopeConstant.IM_PUSH_ONE2ONE)
                    || scopes.contains(HttpAuthScopeConstant.IM_PUSH);
            case GROUP_TEXT -> scopes.contains(HttpAuthScopeConstant.IM_PUSH_GROUP)
                    || scopes.contains(HttpAuthScopeConstant.IM_PUSH);
            case CUSTOMER_SERVICE_TEXT -> scopes.contains(HttpAuthScopeConstant.IM_PUSH_CS)
                    || scopes.contains(HttpAuthScopeConstant.IM_PUSH);
        };
    }

    private static HttpPipelineException forbidden(String message) {
        return HttpJwtAuth.forbidden(message);
    }
}
