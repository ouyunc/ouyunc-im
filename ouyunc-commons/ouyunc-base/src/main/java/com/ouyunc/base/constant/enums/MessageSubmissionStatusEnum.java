package com.ouyunc.base.constant.enums;

/** 客户端消息提交受理状态；该状态与接收方送达、已读状态彼此独立。 */
public enum MessageSubmissionStatusEnum {
    /** 已完成该消息类型定义的受理步骤；不代表接收端送达或已读。 */
    ACCEPTED,
    /** 明确拒绝。修正业务条件后作为新操作提交，不把它当作网络重试。 */
    REJECTED,
    /** 暂不可处理，退避后沿用同一 messageId 和正文重试，不得重新生成业务 ID。 */
    RETRY_LATER,
    /**
     * 操作可能已部分或全部完成。断连、响应丢失也应按此语义处理。
     * 必须在约定重试窗口内沿用原 messageId/正文重试或查询正式结果；
     * 聊天历史补拉只能恢复消息展示，不能代替好友/群审批等操作的完成确认。
     */
    UNKNOWN
}
