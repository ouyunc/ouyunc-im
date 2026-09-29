package com.ouyunc.base.packet;

import com.ouyunc.base.model.Target;

/**
 * 为单次投递复制 Packet。
 *
 * <p>{@link Packet#clone()} 已深拷贝 Message → Metadata → ClusterRoute → Target，
 * 本方法在副本上写入本次 {@code target.clone()}，调用方持有的原包保持不变。
 * 多目标并发投递必须各持一份副本，不能只替换共享 ClusterRoute 上的 Target。</p>
 */
public final class PacketCopyHelper {

    private PacketCopyHelper() {
    }

    /**
     * @param original 原始包，不会被修改
     * @param target   本次投递目标；为 null 时只做深拷贝
     * @return 与 original 不共享 Packet/Message/Metadata/ClusterRoute/Target 的副本
     */
    public static Packet copyForDelivery(Packet original, Target target) {
        if (original == null) {
            return null;
        }
        Packet copy = original.clone();
        if (target == null || copy.getMessage() == null || copy.getMessage().getMetadata() == null) {
            return copy;
        }
        copy.getMessage().getMetadata().ensureClusterRoute().setTarget(target.clone());
        return copy;
    }
}
