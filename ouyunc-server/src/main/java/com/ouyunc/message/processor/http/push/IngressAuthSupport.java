package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.http.HttpContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 推送入口鉴权：校验 appKey 与 metadata 一致，不依赖 Channel 登录态。
 */
public enum IngressAuthSupport {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(IngressAuthSupport.class);

    public boolean verify(Packet packet, HttpContext httpContext) {
        if (packet == null || packet.getMessage() == null || httpContext == null) {
            return false;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || !IngressSourceEnum.isHttpPush(metadata.getIngressSource())) {
            log.warn("HTTP 推送 Packet 缺少 ingressSource 标记");
            return false;
        }
        if (!StringUtils.equals(metadata.getAppKey(), httpContext.getAppKey())) {
            log.warn("HTTP 推送 appKey 不一致: header={}, metadata={}", httpContext.getAppKey(), metadata.getAppKey());
            return false;
        }
        if (StringUtils.isBlank(message.getFrom()) || StringUtils.isBlank(message.getTo())) {
            log.warn("HTTP 推送 from/to 不能为空");
            return false;
        }
        if (StringUtils.isBlank(message.getId())) {
            log.warn("HTTP 推送 messageId 不能为空");
            return false;
        }
        return true;
    }
}
