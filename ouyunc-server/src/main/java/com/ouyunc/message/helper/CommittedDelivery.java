package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.exception.DeliveryRunBusyException;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.support.DeliveryCompletionSupport;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 已提交消息的首次扇出只执行一次。
 * <p>
 * 未读失败或进程在投递前退出时，重复请求会再次进入这里补投。
 * 已经完成的正式 packet 直接返回，避免把成功重试再广播一遍。
 */
public final class CommittedDelivery {

    private CommittedDelivery() {
    }

    public static void run(Packet packet, Runnable action) {
        run(packet, ignored -> action.run());
    }

    /**
     * 需要长时间分批处理的投递可在批次边界调用 {@link DeliveryLease#renew()}。
     * 续租会校验 owner，锁已丢失时立即中止后续扇出。
     */
    public static void run(Packet packet, Consumer<DeliveryLease> action) {
        String ownerToken = UUID.randomUUID().toString();
        DeliveryCompletionSupport.RunState state = DefaultRepository.INSTANCE.tryStartDelivery(packet, ownerToken);
        if (state == DeliveryCompletionSupport.RunState.DONE) {
            return;
        }
        if (state != DeliveryCompletionSupport.RunState.ACQUIRED) {
            throw new DeliveryRunBusyException("首次扇出正在进行, packetId=" + (packet == null ? null : packet.getPacketId()));
        }
        try {
            action.accept(new DeliveryLease(packet, ownerToken));
            if (!DefaultRepository.INSTANCE.finishDelivery(packet, ownerToken)) {
                throw new DeliveryRunBusyException("首次扇出执行权已失效, packetId="
                        + (packet == null ? null : packet.getPacketId()));
            }
        } catch (RuntimeException | Error error) {
            DefaultRepository.INSTANCE.abortDelivery(packet, ownerToken);
            throw error;
        }
    }

    public static final class DeliveryLease {
        private static final long RENEW_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(
                Math.max(1L, MessageConstant.DELIVERY_RUN_LOCK_MILLIS / 3L));

        private final Packet packet;
        private final String ownerToken;
        private long nextRenewNanos;

        private DeliveryLease(Packet packet, String ownerToken) {
            this.packet = packet;
            this.ownerToken = ownerToken;
            this.nextRenewNanos = System.nanoTime() + RENEW_INTERVAL_NANOS;
        }

        /**
         * 在下一批产生外部副作用前做续租检查。批次可高频调用，实际 Redis 续租按 TTL/3 节流；
         * 如果 JVM 停顿或单批处理过慢导致 owner 丢失，则立即中止后续投递。
         */
        public void renew() {
            long now = System.nanoTime();
            if (now < nextRenewNanos) {
                return;
            }
            if (!DefaultRepository.INSTANCE.renewDelivery(packet, ownerToken)) {
                throw new DeliveryRunBusyException("首次扇出续租失败, packetId="
                        + (packet == null ? null : packet.getPacketId()));
            }
            nextRenewNanos = now + RENEW_INTERVAL_NANOS;
        }
    }
}
