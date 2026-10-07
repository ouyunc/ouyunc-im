package com.ouyunc.base.constant.enums;

/**
 * 群组消息推送模式枚举
 *
 * @author ouyunc
 */
public enum GroupMessagePushModeEnum {
    /**
     * 拉取
     */
    PULL,

    /**
     * 推送
     */
    PUSH,

    /**
     * 小群全员推送，成员数超过 threshold 时只推 @，其余成员拉取。
     * 未配置时按此模式，避免空值把群消息静默丢掉。
     */
    PULL_PUSH;

    /**
     * 空配置回退到小群推、大群拉。
     *
     * @param mode 配置值，可为 null
     * @return 实际使用的模式
     */
    public static GroupMessagePushModeEnum orDefault(GroupMessagePushModeEnum mode) {
        return mode == null ? PULL_PUSH : mode;
    }
}
