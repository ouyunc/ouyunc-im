package com.ouyunc.message.processor.http.push;

import com.alibaba.fastjson2.JSONObject;
import com.ouyunc.base.constant.ChannelMessageExtraKeys;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.model.Metadata;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * HTTP 推送 extra 中的渠道字段应用到 Metadata / ingress。
 */
public final class ChannelMessagePushSupport {

    private ChannelMessagePushSupport() {
    }

    /**
     * @param preserveHttpPushIngress {@code true} 时不覆盖已标记的 HTTP_PUSH（HTTP 入口鉴权依赖该标记）
     */
    public static void applyExtensions(Metadata metadata, Map<String, Object> extensions,
                                       boolean preserveHttpPushIngress) {
        if (metadata == null || extensions == null || extensions.isEmpty()) {
            return;
        }
        if (preserveHttpPushIngress && IngressSourceEnum.isHttpPush(metadata.getIngressSource())) {
            return;
        }
        Object ingressChannel = extensions.get(ChannelMessageExtraKeys.INGRESS_CHANNEL);
        if (ingressChannel != null) {
            String key = String.valueOf(ingressChannel);
            IngressSourceEnum ingress = mapIngressSource(key);
            if (ingress != null) {
                metadata.setIngressSource(ingress.getCode());
            }
        }
        Object ingressSource = extensions.get(ChannelMessageExtraKeys.INGRESS_SOURCE);
        if (ingressSource != null && StringUtils.isNotBlank(String.valueOf(ingressSource))) {
            metadata.setIngressSource(String.valueOf(ingressSource));
        }
    }

    public static void applyExtensions(Metadata metadata, Map<String, Object> extensions) {
        applyExtensions(metadata, extensions, false);
    }

    private static IngressSourceEnum mapIngressSource(String channelKey) {
        MessageDeliveryChannelEnum channel = MessageDeliveryChannelEnum.fromKey(channelKey);
        return switch (channel) {
            case WHATSAPP -> IngressSourceEnum.WHATSAPP_WEBHOOK;
            case TELEGRAM -> IngressSourceEnum.TELEGRAM_WEBHOOK;
            default -> null;
        };
    }

    public static JSONObject mergeDeliveryChannelExtra(Integer deliveryChannelCode) {
        if (deliveryChannelCode == null) {
            return null;
        }
        JSONObject extra = new JSONObject();
        extra.put(ChannelMessageExtraKeys.DELIVERY_CHANNEL, deliveryChannelCode);
        return extra;
    }
}
