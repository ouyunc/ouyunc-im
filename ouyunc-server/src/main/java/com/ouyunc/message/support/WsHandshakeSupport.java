package com.ouyunc.message.support;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.AuthenticationHandler;
import com.ouyunc.message.handler.Convert2PacketHandler;
import com.ouyunc.message.handler.ExceptionHandler;
import com.ouyunc.message.handler.PacketHandler;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.LoginAuthValidator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * WS 握手：URI/字段形态在 EventLoop 上做完；签名与配额下沉到业务线程，避免连接风暴堵 IO。
 */
final class WsHandshakeSupport {

    private static final Logger log = LoggerFactory.getLogger(WsHandshakeSupport.class);

    private static final WebSocketExtensionFilterProvider WS_FILTER_PROVIDER = new WebSocketExtensionFilterProvider() {
        private final WebSocketExtensionFilter thresholdFilter = frame ->
                frame.content() != null
                        && frame.content().readableBytes() < MessageConstant.WEBSOCKET_COMPRESSION_THRESHOLD;

        @Override
        public WebSocketExtensionFilter encoderFilter() {
            return thresholdFilter;
        }

        @Override
        public WebSocketExtensionFilter decoderFilter() {
            return WebSocketExtensionFilter.NEVER_SKIP;
        }
    };

    private WsHandshakeSupport() {
    }

    static void dispatch(ChannelHandlerContext ctx, FullHttpRequest request) {
        HandshakeGate gate = parseGate(ctx, request);
        if (gate == null) {
            ctx.close();
            return;
        }
        try {
            request.setUri(new URI(request.uri()).getPath());
        } catch (URISyntaxException e) {
            log.error("客户端连接失败,原因：uri解析失败！正在关闭channel :{}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        request.retain();
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> verifyThenInstall(ctx, request, gate));
        } catch (RejectedExecutionException e) {
            log.error("WS 握手任务被拒绝 channelId={}", ctx.channel().id().asShortText(), e);
            request.release();
            ctx.close();
        }
    }

    private static HandshakeGate parseGate(ChannelHandlerContext ctx, FullHttpRequest request) {
        Map<String, Object> query;
        try {
            query = HttpUtil.wrapParams2Map(new URI(request.uri()).getQuery());
        } catch (URISyntaxException e) {
            log.error("客户端连接失败,原因：uri解析失败！正在关闭channel :{}", ctx.channel().id().asShortText());
            return null;
        }
        String appKey = firstQuery(query, LoginContent.Fields.APP_KEY, LoginContent.Fields.APP_KEY_SNAKE);
        if (StringUtils.isBlank(appKey)) {
            log.error("WS 握手缺少 appKey，拒绝连接 channelId={}", ctx.channel().id().asShortText());
            return null;
        }
        LoginContent loginContent = parseOptionalSignature(query, appKey);
        if (loginContent == HandshakeGate.INVALID_SIGNATURE) {
            return null;
        }
        return new HandshakeGate(appKey, loginContent);
    }

    /**
     * @return null 表示无签名等 LOGIN；INVALID_SIGNATURE 表示字段不合法
     */
    private static LoginContent parseOptionalSignature(Map<String, Object> query, String appKey) {
        String identity = firstQuery(query, LoginContent.Fields.IDENTITY, LoginContent.Fields.USER_ID, LoginContent.Fields.USER_ID_SNAKE);
        String signature = firstQuery(query, LoginContent.Fields.SIGNATURE, LoginContent.Fields.SIGN);
        String createTimeRaw = firstQuery(query, LoginContent.Fields.CREATE_TIME, LoginContent.Fields.CREATE_TIME_SNAKE);
        int present = countPresent(identity, signature, createTimeRaw);
        if (present == NumberConstant.NUMBER_0) {
            return null;
        }
        if (present < MessageConstant.WS_HANDSHAKE_SIGNATURE_FIELD_COUNT) {
            log.warn("WS 握手签名字段不全，拒绝连接");
            return HandshakeGate.INVALID_SIGNATURE;
        }
        long createTime;
        try {
            createTime = Long.parseLong(createTimeRaw.trim());
        } catch (NumberFormatException e) {
            log.warn("WS 握手 createTime 非法: {}", createTimeRaw);
            return HandshakeGate.INVALID_SIGNATURE;
        }
        LoginContent loginContent = new LoginContent();
        loginContent.setAppKey(appKey);
        loginContent.setIdentity(identity);
        loginContent.setSignature(signature);
        loginContent.setCreateTime(createTime);
        loginContent.setScope(LoginScopeEnum.NORMAL.getType());
        String algoRaw = firstQuery(query, LoginContent.Fields.SIGNATURE_ALGORITHM, LoginContent.Fields.SIGN_ALGO);
        if (StringUtils.isNotBlank(algoRaw)) {
            try {
                loginContent.setSignatureAlgorithm(Byte.parseByte(algoRaw.trim()));
            } catch (NumberFormatException ignored) {
                // 默认 MD5
            }
        }
        return loginContent;
    }

    private static void verifyThenInstall(ChannelHandlerContext ctx, FullHttpRequest request, HandshakeGate gate) {
        Channel channel = ctx.channel();
        try {
            if (!channel.isActive()) {
                request.release();
                return;
            }
            if (gate.loginContent() != null && !LoginAuthValidator.verify(gate.loginContent())) {
                reject(ctx, request, "签名验证失败");
                return;
            }
            if (!AppKeyValidator.INSTANCE.tryReserveForLogin(gate.appKey(), ctx)) {
                reject(ctx, request, "验证统计AppKey连接数超过允许的最大值");
                return;
            }
        } catch (Exception e) {
            log.error("WS 握手校验异常 channelId={}", channel.id().asShortText(), e);
            AppKeyValidator.releaseReservedIfNeeded(gate.appKey(), ctx);
            reject(ctx, request, "握手校验异常");
            return;
        }
        channel.eventLoop().execute(() -> finishOnEventLoop(ctx, request, gate));
    }

    private static void finishOnEventLoop(ChannelHandlerContext ctx, FullHttpRequest request, HandshakeGate gate) {
        Channel channel = ctx.channel();
        if (!channel.isActive()) {
            AppKeyValidator.releaseReservedIfNeeded(gate.appKey(), ctx);
            request.release();
            return;
        }
        boolean handedOff = false;
        try {
            ctx.channel().attr(NativePacketProtocol.protocolAttrKey).set(NativePacketProtocol.WS);
            installPipeline(ctx.pipeline());
            ctx.fireChannelActive();
            ctx.fireChannelRead(request);
            handedOff = true;
        } catch (Exception e) {
            log.error("WS 握手安装管道失败 channelId={}", channel.id().asShortText(), e);
            AppKeyValidator.releaseReservedIfNeeded(gate.appKey(), ctx);
            ctx.close();
        } finally {
            if (!handedOff && request.refCnt() > NumberConstant.NUMBER_0) {
                request.release();
            }
        }
    }

    private static void installPipeline(ChannelPipeline pipeline) {
        pipeline
                .addLast(MessageConstant.WS_FRAME_AGGREGATOR_HANDLER,
                        new WebSocketFrameAggregator(MessageConstant.MAX_WEBSOCKET_FRAME_SIZE))
                .addLast(MessageConstant.WS_COMPRESSION_HANDLER, new WebSocketServerExtensionHandler(
                        new PerMessageDeflateServerExtensionHandshaker(
                                NumberConstant.NUMBER_6,
                                true,
                                NumberConstant.NUMBER_15,
                                true,
                                false,
                                WS_FILTER_PROVIDER,
                                MessageConstant.MAX_WEBSOCKET_FRAME_SIZE
                        ), new DeflateFrameServerExtensionHandshaker(
                                DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL,
                                MessageConstant.MAX_WEBSOCKET_FRAME_SIZE)))
                .addLast(MessageConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler(
                        MessageServerContext.serverProperties().getWebsocketPath(),
                        null, true, MessageConstant.MAX_WEBSOCKET_FRAME_SIZE))
                .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                .addLast(MessageConstant.PACKET_HANDLER, PacketHandler.client())
                .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler())
                .remove(MessageConstant.HTTP_DISPATCHER_HANDLER);
        if (MessageServerContext.serverProperties().isServerLoginEnable()) {
            pipeline.addBefore(MessageConstant.PACKET_HANDLER, MessageConstant.AUTHENTICATION_HANDLER,
                    new AuthenticationHandler());
        }
    }

    private static void reject(ChannelHandlerContext ctx, FullHttpRequest request, String reason) {
        log.error("客户端连接失败,原因：{}！", reason);
        request.release();
        ctx.close();
    }

    private static int countPresent(String... values) {
        int n = NumberConstant.NUMBER_0;
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                n++;
            }
        }
        return n;
    }

    private static String firstQuery(Map<String, Object> queryParamsMap, String... keys) {
        if (queryParamsMap == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = queryParamsMap.get(key);
            if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private record HandshakeGate(String appKey, LoginContent loginContent) {
        static final LoginContent INVALID_SIGNATURE = new LoginContent();
    }
}
