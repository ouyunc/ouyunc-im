package com.ouyunc.repository;

/**
 * 消息落库结果（含 QoS 幂等冲突）
 */
public enum SaveMessageOutcome {
    /** 落库成功 */
    SUCCESS,
    /** Redis/序列化等失败 */
    FAILED,
    /** 命中 QoS 幂等，无需重复落库 */
    DUPLICATE
}
