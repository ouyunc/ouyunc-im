package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.constant.enums.ContentSafetyProviderEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户内容安全策略。
 * <p>存 Redis JSON（及 MySQL json_config），管理端覆盖；IM 节点 Caffeine 缓存 30 分钟或 Pub/Sub 失效。</p>
 */
public class ContentSafetyPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否启用文本敏感词；false 时跳过文本检查。 */
    private boolean textEnabled = true;
    /** 文本命中动作：MASK / REJECT / AUDIT_ONLY / PASS。 */
    private ContentSafetyAction textAction = ContentSafetyAction.MASK;
    /** 脱敏替换字符，取首字符；空则用 *。 */
    private String textMaskChar = "*";

    /** 是否启用媒体审核标记（P0 仅打 metadata；闭环见 P1）。 */
    private boolean mediaEnabled = true;
    /** 媒体动作：SEND_THEN_REVIEW / HOLD。 */
    private ContentSafetyAction mediaAction = ContentSafetyAction.SEND_THEN_REVIEW;
    /** 监黄供应商。 */
    private ContentSafetyProviderEnum provider = ContentSafetyProviderEnum.ALIYUN;
    /** 审核场景标识，透传给供应商（供应商侧字符串，保持 String）。 */
    private String scene = "im_chat";
    /** 先发后审失败时是否放行（fail-open）。 */
    private boolean failOpen = true;
    /** HOLD 超时失败时是否放行；默认 false 即超时按拒绝处理。 */
    private boolean holdFailOpen = false;
    /** HOLD 等待审核超时毫秒，默认 5 分钟。 */
    private long holdTimeoutMs = 300_000L;
    /** 审核结果是否通知发送方。 */
    private boolean notifySender = true;
    /** 事后违规是否自动系统撤回。 */
    private boolean autoWithdraw = true;
    /** 系统撤回是否跳过用户 2 分钟时间窗。 */
    private boolean systemWithdrawBypassWindow = true;

    /**
     * @return 字段全部为默认值的策略实例
     */
    public static ContentSafetyPolicy defaults() {
        return new ContentSafetyPolicy();
    }

    /**
     * @return 是否检查文本敏感词
     */
    public boolean isTextEnabled() {
        return textEnabled;
    }

    /**
     * @param textEnabled 是否检查文本
     */
    public void setTextEnabled(boolean textEnabled) {
        this.textEnabled = textEnabled;
    }

    /**
     * @return 文本动作，空则 MASK
     */
    public ContentSafetyAction getTextAction() {
        return textAction == null ? ContentSafetyAction.MASK : textAction;
    }

    /**
     * @param textAction 文本命中动作
     */
    public void setTextAction(ContentSafetyAction textAction) {
        this.textAction = textAction;
    }

    /**
     * @return 脱敏字符，空则 *
     */
    public String getTextMaskChar() {
        return textMaskChar == null || textMaskChar.isBlank() ? "*" : textMaskChar;
    }

    /**
     * @param textMaskChar 脱敏字符
     */
    public void setTextMaskChar(String textMaskChar) {
        this.textMaskChar = textMaskChar;
    }

    /**
     * @return 是否处理媒体
     */
    public boolean isMediaEnabled() {
        return mediaEnabled;
    }

    /**
     * @param mediaEnabled 是否处理媒体
     */
    public void setMediaEnabled(boolean mediaEnabled) {
        this.mediaEnabled = mediaEnabled;
    }

    /**
     * @return 媒体动作，空则 SEND_THEN_REVIEW
     */
    public ContentSafetyAction getMediaAction() {
        return mediaAction == null ? ContentSafetyAction.SEND_THEN_REVIEW : mediaAction;
    }

    /**
     * @param mediaAction 媒体动作
     */
    public void setMediaAction(ContentSafetyAction mediaAction) {
        this.mediaAction = mediaAction;
    }

    /**
     * @return 供应商，空则 ALIYUN
     */
    public ContentSafetyProviderEnum getProvider() {
        return provider == null ? ContentSafetyProviderEnum.ALIYUN : provider;
    }

    /**
     * @param provider 供应商
     */
    public void setProvider(ContentSafetyProviderEnum provider) {
        this.provider = provider;
    }

    /**
     * @return 审核场景
     */
    public String getScene() {
        return scene;
    }

    /**
     * @param scene 审核场景
     */
    public void setScene(String scene) {
        this.scene = scene;
    }

    /**
     * @return 先发后审失败是否放行
     */
    public boolean isFailOpen() {
        return failOpen;
    }

    /**
     * @param failOpen 失败是否放行
     */
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    /**
     * @return HOLD 超时失败是否放行
     */
    public boolean isHoldFailOpen() {
        return holdFailOpen;
    }

    /**
     * @param holdFailOpen HOLD 超时失败是否放行
     */
    public void setHoldFailOpen(boolean holdFailOpen) {
        this.holdFailOpen = holdFailOpen;
    }

    /**
     * @return HOLD 超时毫秒
     */
    public long getHoldTimeoutMs() {
        return holdTimeoutMs;
    }

    /**
     * @param holdTimeoutMs HOLD 超时毫秒
     */
    public void setHoldTimeoutMs(long holdTimeoutMs) {
        this.holdTimeoutMs = holdTimeoutMs;
    }

    /**
     * @return 是否通知发送方
     */
    public boolean isNotifySender() {
        return notifySender;
    }

    /**
     * @param notifySender 是否通知发送方
     */
    public void setNotifySender(boolean notifySender) {
        this.notifySender = notifySender;
    }

    /**
     * @return 违规是否自动撤回
     */
    public boolean isAutoWithdraw() {
        return autoWithdraw;
    }

    /**
     * @param autoWithdraw 是否自动撤回
     */
    public void setAutoWithdraw(boolean autoWithdraw) {
        this.autoWithdraw = autoWithdraw;
    }

    /**
     * @return 系统撤回是否跳过时间窗
     */
    public boolean isSystemWithdrawBypassWindow() {
        return systemWithdrawBypassWindow;
    }

    /**
     * @param systemWithdrawBypassWindow 是否跳过时间窗
     */
    public void setSystemWithdrawBypassWindow(boolean systemWithdrawBypassWindow) {
        this.systemWithdrawBypassWindow = systemWithdrawBypassWindow;
    }
}
