package com.ouyunc.message.helper;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.QosClaim;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.repository.support.QosIdempotencyHelper;
import org.apache.commons.lang3.StringUtils;

/**
 * 固定客户端原始请求指纹。
 *
 * <p>调用时机必须位于可信身份校验之后、内容安全 MASK、引用/@ 规范化以及客服入口身份改写之前。
 * 后续 QoS claim/commit 都优先使用该指纹，使同一原始请求的重试不受展示内容变化影响。</p>
 */
public final class QosRequestFingerprintSupport {

    private QosRequestFingerprintSupport() {
    }

    /** 首次调用计算并保存指纹；重复调用保持首次值，禁止用处理后的消息覆盖原请求身份。 */
    public static void captureIfAbsent(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null) {
            return;
        }
        QosClaim claim = metadata.ensureQosClaim();
        if (StringUtils.isBlank(claim.getQosPayloadHash())) {
            claim.setQosPayloadHash(QosIdempotencyHelper.payloadHash(packet.getMessage()));
        }
    }
}
