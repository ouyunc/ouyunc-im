package com.ouyunc.message.helper;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.DeliveryRunBusyException;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.support.DeliveryCompletionSupport;

import java.util.UUID;

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
        String ownerToken = UUID.randomUUID().toString();
        DeliveryCompletionSupport.RunState state = DefaultRepository.INSTANCE.tryStartDelivery(packet, ownerToken);
        if (state == DeliveryCompletionSupport.RunState.DONE) {
            return;
        }
        if (state != DeliveryCompletionSupport.RunState.ACQUIRED) {
            throw new DeliveryRunBusyException("首次扇出正在进行, packetId=" + (packet == null ? null : packet.getPacketId()));
        }
        try {
            action.run();
            DefaultRepository.INSTANCE.finishDelivery(packet, ownerToken);
        } catch (RuntimeException | Error error) {
            DefaultRepository.INSTANCE.abortDelivery(packet, ownerToken);
            throw error;
        }
    }
}
