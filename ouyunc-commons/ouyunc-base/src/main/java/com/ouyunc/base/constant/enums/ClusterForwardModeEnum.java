package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * 集群转发意图：落地后写客户端，还是只进本机集群 Processor。
 * <p>下一跳只是手段；本枚举描述最终节点做什么。仅服务端 Metadata 使用，不下发客户端。</p>
 */
public enum ClusterForwardModeEnum {

    /** 尚未集群转发（客户端入站 / 本机刚生成）。 */
    NONE(NumberConstant.NUMBER_0),

    /** 落地写给客户端，不进业务 Processor。 */
    CLIENT(NumberConstant.NUMBER_1),

    /** 落地进集群 Processor，不写客户端（如 QOS_RETRY_CANCEL）。 */
    INTERNAL(NumberConstant.NUMBER_2);

    private final byte value;

    ClusterForwardModeEnum(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static ClusterForwardModeEnum orNone(ClusterForwardModeEnum mode) {
        return mode == null ? NONE : mode;
    }
}
