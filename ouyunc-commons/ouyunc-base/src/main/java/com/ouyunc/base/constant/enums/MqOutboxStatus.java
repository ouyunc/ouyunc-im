package com.ouyunc.base.constant.enums;

/**
 * {@code ouyunc_im_mq_outbox.status}：MQ 旁路失败补发任务状态。
 */
public enum MqOutboxStatus {

    /** 待投递 / 可重试 */
    PENDING(0),
    /** 投递中（认领后） */
    SENDING(1),
    /** 已成功（可选保留；当前实现成功后直接删行） */
    SENT(2),
    /** 超限死信，需人工或慢速处理 */
    DEAD(3);

    private final int code;

    MqOutboxStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
