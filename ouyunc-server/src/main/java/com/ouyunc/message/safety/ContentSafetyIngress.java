package com.ouyunc.message.safety;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.helper.MessageSendResultHelper;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入站内容安全：在连接有序任务线程上检查，与业务 process 同序。
 * <p>不是 Netty Handler，禁止挂到 EventLoop 管道。</p>
 */
public final class ContentSafetyIngress {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyIngress.class);

    private ContentSafetyIngress() {
    }

    /**
     * 敏感词检查；REJECT 时回写并不再进入业务。检查异常放行，避免误杀。PING 不要调用。
     *
     * @param ctx    通道上下文
     * @param packet 协议包
     * @return {@code true} 继续业务；{@code false} 已拒绝
     */
    public static boolean applyOnWorker(ChannelHandlerContext ctx, Packet packet) {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            log.error("内容安全检查异常，放行以免误杀 packetId={}", packet == null ? null : packet.getPacketId(), e);
            return true;
        }
        if (result != null && !result.isPassed()) {
            log.warn("内容安全拒绝 packetId={} reason={} hits={}",
                    packet.getPacketId(), result.getReason(), result.getHitWords());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT, ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage(), "ContentSafetyIngress.applyOnWorker", packet);
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT);
            return false;
        }
        return true;
    }

}
