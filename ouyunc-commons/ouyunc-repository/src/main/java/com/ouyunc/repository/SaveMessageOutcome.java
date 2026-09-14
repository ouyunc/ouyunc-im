package com.ouyunc.repository;

/**
 * 消息落库结果（含 QoS 幂等冲突）
 */
public enum SaveMessageOutcome {
    /** 落库成功 */
    SUCCESS,
    /** Redis/序列化等失败 */
    FAILED,
    /** 相同幂等键对应了不同正文 */
    CONFLICT,
    /** 已提交的 QoS 幂等消息，无需重复落库 */
    DUPLICATE
}
