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
    DUPLICATE;

    /** 本次新写入，可做未读/last/扇出等副作用。 */
    public boolean isFreshWrite() {
        return this == SUCCESS;
    }

    /** 幂等命中：可回 ACK，禁止再次投递或累加未读。 */
    public boolean isDuplicate() {
        return this == DUPLICATE;
    }

    /** 未写入成功，不能 ACK、不能投递。 */
    public boolean isFailed() {
        return this == FAILED || this == CONFLICT;
    }
}
