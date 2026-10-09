package com.ouyunc.base.constant.enums;

/**
 * 消息落库结果（含 QoS 幂等冲突）
 */
public enum SaveMessageOutcomeEnum {
    /** 落库成功 */
    SUCCESS,
    /** Redis/序列化等失败 */
    FAILED,
    /** 存储调用结果未知；不得据此删除可能已提交的业务数据。 */
    UNKNOWN,
    /** 相同幂等键对应了不同正文 */
    CONFLICT,
    /** 已提交的 QoS 幂等消息，无需重复落库 */
    DUPLICATE;

    /** 本次新写入，可做未读/last/扇出等副作用。 */
    public boolean isFreshWrite() {
        return this == SUCCESS;
    }

    /** 幂等命中：正文已提交；完成必要的索引修复和投递收口后才可 ACK。 */
    public boolean isDuplicate() {
        return this == DUPLICATE;
    }

    /** 尚不能确认受理完成，不能 ACK；UNKNOWN 不代表所有写入均未执行。 */
    public boolean isFailed() {
        return this == FAILED || this == CONFLICT || this == UNKNOWN;
    }
}
