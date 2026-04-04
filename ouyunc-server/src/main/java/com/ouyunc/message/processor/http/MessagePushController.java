package com.ouyunc.message.processor.http;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpPushChannelEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.domain.http.MessagePushExtra;
import com.ouyunc.domain.http.MessagePushRequest;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

/**
 * IM HTTP 推送（类似 Spring {@code @RestController} + 方法映射）。
 */
@HttpRestController
@HttpRequestMapping("/api/im")
public class MessagePushController {

    private static final Logger log = LoggerFactory.getLogger(MessagePushController.class);

    private static final String DEFAULT_FROM = "system";

    private static final String MDC_REQUEST_ID = "requestId";

    @PostHttpRequest("/push")
    public Object push(@RequestBody MessagePushRequest body, HttpContext httpContext) throws HttpPipelineException {
        if (body == null) {
            return null;
        }
        String requestId = StringUtils.trimToNull(httpContext.getRequest().headers().get(HttpRequestConstant.HTTP_HEADER_REQUEST_ID));
        if (requestId != null) {
            MDC.put(MDC_REQUEST_ID, requestId);
        }
        try {
            String appKey = httpContext.getAppKey();
            if (StringUtils.isBlank(appKey)) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                        "缺少 appKey（请在请求头设置 " + HttpRequestConstant.HTTP_HEADER_APP_KEY + "）");
            }
            if (StringUtils.isBlank(body.getMessageId())) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "messageId 不能为空");
            }
            String messageId = body.getMessageId().trim();

            validatePushChannel(body.getPushChannel());
            PushSemantics pushSemantics = resolvePushSemantics(body.getPushType());

            if (StringUtils.isBlank(body.getTo())) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "to 不能为空");
            }
            int contentType = pushSemantics.contentType();
            long createTime = body.getCreateTime() != null ? body.getCreateTime() : TimeUtil.currentTimeMillis();
            String from = StringUtils.isNotBlank(body.getFrom()) ? body.getFrom() : DEFAULT_FROM;

            int fromTypeValue = resolveIdentityType(body.getFromType());
            int toTypeValue = resolveIdentityType(body.getToType());

            Metadata metadata = new Metadata();
            metadata.setAppKey(appKey);
            metadata.setClientIp(IpUtil.getIp(httpContext.getChannelContext()));
            metadata.setServerTime(TimeUtil.currentTimeMillis());

            List<String> at = null;
            List<String> ref = null;
            int qos = 0;
            String extraJson = null;
            MessagePushExtra ex = body.getExtra();
            if (ex != null) {
                if (CollectionUtils.isNotEmpty(ex.getAt())) {
                    at = ex.getAt();
                }
                if (CollectionUtils.isNotEmpty(ex.getRef())) {
                    ref = ex.getRef();
                }
                if (ex.getQos() != null) {
                    qos = ex.getQos();
                }
                if (ex.getExtensions() != null && !ex.getExtensions().isEmpty()) {
                    extraJson = JSON.toJSONString(ex.getExtensions());
                }
            }
            Message message = new Message(messageId, from, fromTypeValue, body.getTo(), toTypeValue, contentType,
                    body.getContent(), at, ref, extraJson, qos, createTime, metadata);

            MessageTypeEnum packetMessageType = pushSemantics.packetMessageType();
            byte msgTypeByte = packetMessageType.getType() == null ? MessageTypeEnum.ONE_2_ONE.getType() : packetMessageType.getType();
            Packet packet = new Packet(
                    NativePacketProtocol.HTTP.getProtocol(),
                    NativePacketProtocol.HTTP.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(),
                    DeviceTypeEnum.PC.getType(),
                    NetworkEnum.OTHER.getValue(),
                    Encrypt.SymmetryEncrypt.NONE.getValue(),
                    Serializer.PROTO_STUFF.getValue(),
                    msgTypeByte,
                    message);

            List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, body.getTo());
            if (CollectionUtils.isEmpty(targets)) {
                log.warn("HTTP push: recipient offline, appKey={}, to={}", appKey, body.getTo());
                return Boolean.FALSE;
            }
            MessageHelper.asyncSendMessage(packet, targets);
            return Boolean.TRUE;
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static void validatePushChannel(Integer pushChannel) throws HttpPipelineException {
        HttpPushChannelEnum ch = HttpPushChannelEnum.resolve(pushChannel);
        if (ch == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "不支持的 pushChannel: " + pushChannel + "，当前仅支持 0（长连接 IM）");
        }
        if (ch != HttpPushChannelEnum.IM) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "不支持的推送渠道: " + ch);
        }
    }

    /**
     * {@link PushTypeEnum} → 内线 {@link MessageTypeEnum} + {@link MessageContentTypeEnum}（HTTP 文本类推送当前均为 {@link MessageContentTypeEnum#TEXT_CONTENT}）。
     */
    private record PushSemantics(MessageTypeEnum packetMessageType, int contentType) {
    }

    private static PushSemantics resolvePushSemantics(Integer pushType) throws HttpPipelineException {
        int code = pushType == null ? PushTypeEnum.USER.getType() : pushType;
        PushTypeEnum t = PushTypeEnum.getPushTypeEnum(code);
        if (t == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "未知 pushType: " + pushType);
        }
        int textContentType = MessageContentTypeEnum.TEXT_CONTENT.getType();
        return switch (t) {
            case USER -> new PushSemantics(MessageTypeEnum.ONE_2_ONE, textContentType);
            case CUSTOMER_SERVICE -> new PushSemantics(MessageTypeEnum.CUSTOMER_SERVICE, textContentType);
            default -> throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "当前 HTTP 推送仅支持 pushType=1（单用户）或 pushType=4（客服会话），to 为接收方 identity");
        };
    }

    private static int resolveIdentityType(Integer value) throws HttpPipelineException {
        if (value == null) {
            return IdentityType.ONE_2_ONE.value();
        }
        IdentityType t = IdentityType.valueOf(value);
        if (t == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "非法 fromType/toType: " + value);
        }
        return t.value();
    }
}
