package com.ouyunc.base.utils;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * QoS 幂等 client 键的发送方身份：优先 {@link Metadata#getQosClaimIdentity()}，否则 {@code message.from}。
 * 使用场景：客服把 from 改写成入口虚拟号后，claim/release/判重仍应对齐登录坐席/访客。
 */
public final class QosClaimIdentities {

    private QosClaimIdentities() {
    }

    public static String resolve(Message message) {
        if (message == null) {
            return null;
        }
        Metadata metadata = message.getMetadata();
        if (metadata != null && StringUtils.isNotBlank(metadata.getQosClaimIdentity())) {
            return metadata.getQosClaimIdentity();
        }
        return message.getFrom();
    }

    /** 仅在尚未记录时写入，避免覆盖 AuthValidator 已绑定的登录身份。 */
    public static void rememberIfAbsent(Message message, String loginIdentity) {
        if (message == null || StringUtils.isBlank(loginIdentity)) {
            return;
        }
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isNotBlank(metadata.getQosClaimIdentity())) {
            return;
        }
        metadata.setQosClaimIdentity(loginIdentity);
    }
}
