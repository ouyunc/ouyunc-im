package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
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

import com.ouyunc.base.model.LoginClientInfo;

import java.util.List;

/**
 * IM HTTP 推送（类似 Spring {@code @RestController} + 方法映射）。
 */
@HttpRestController
@HttpRequestMapping("/api/im")
public class MessagePushController {

    private static final Logger log = LoggerFactory.getLogger(MessagePushController.class);

    private static final String DEFAULT_FROM = "system";

    @PostHttpRequest("/push")
    public Object push(@RequestBody MessagePushRequest body, HttpContext httpContext) throws HttpPipelineException {
        if (body == null) {
            return null;
        }
        String appKey = httpContext.getAppKey();
        if (StringUtils.isBlank(appKey)) {
            appKey = body.getAppKey();
        }
        if (StringUtils.isBlank(body.getTo())) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "to 不能为空");
        }
        int contentType = MessageContentTypeEnum.TEXT_CONTENT.getType();
        Integer ct = body.getContentType();
        if (ct != null) {
            contentType = ct;
        }
        long createTime = body.getCreateTime() != null ? body.getCreateTime() : TimeUtil.currentTimeMillis();
        int qos = 0;
        Integer q = body.getQos();
        if (q != null) {
            qos = q;
        }
        String from = StringUtils.isNotBlank(body.getFrom()) ? body.getFrom() : DEFAULT_FROM;

        Metadata metadata = new Metadata();
        metadata.setAppKey(appKey);
        metadata.setClientIp(IpUtil.getIp(httpContext.getChannelContext()));
        metadata.setServerTime(TimeUtil.currentTimeMillis());

        String messageId = String.valueOf(MessageContext.idGenerator().generateId());
        Message message = new Message(messageId, from, body.getTo(), contentType, body.getContent(), qos, createTime, metadata);
        message.setFromType(IdentityType.ONE_2_ONE.value());
        message.setToType(IdentityType.ONE_2_ONE.value());

        Packet packet = new Packet(
                NativePacketProtocol.HTTP.getProtocol(),
                NativePacketProtocol.HTTP.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                DeviceTypeEnum.PC.getType(),
                NetworkEnum.OTHER.getValue(),
                Encrypt.SymmetryEncrypt.NONE.getValue(),
                Serializer.PROTO_STUFF.getValue(),
                MessageTypeEnum.ONE_2_ONE.getType(),
                message);

        List<LoginClientInfo> targets = ClientHelper.onlineAll(appKey, body.getTo());
        if (CollectionUtils.isEmpty(targets)) {
            log.warn("HTTP push: recipient offline, appKey={}, to={}", appKey, body.getTo());
            return Boolean.FALSE;
        }
        MessageHelper.asyncSendMessage(packet, targets);
        return Boolean.TRUE;
    }
}
