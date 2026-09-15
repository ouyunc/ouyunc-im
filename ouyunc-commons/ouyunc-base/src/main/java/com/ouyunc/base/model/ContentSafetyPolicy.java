package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ContentSafetyAction;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户内容安全策略（存 Redis JSON，可被管理端覆盖）。
 */
public class ContentSafetyPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private boolean textEnabled = true;
    /** 默认 MASK，可配 REJECT / MASK / AUDIT_ONLY */
    private ContentSafetyAction textAction = ContentSafetyAction.MASK;
    private String textMaskChar = "*";

    private boolean mediaEnabled = true;
    /** 默认先发后审，可配 HOLD */
    private ContentSafetyAction mediaAction = ContentSafetyAction.SEND_THEN_REVIEW;
    private String provider = "ALIYUN";
    private String scene = "im_chat";
    private boolean failOpen = true;
    private boolean holdFailOpen = false;
    private long holdTimeoutMs = 300_000L;
    private boolean notifySender = true;
    private boolean autoWithdraw = true;
    private boolean systemWithdrawBypassWindow = true;

    public static ContentSafetyPolicy defaults() {
        return new ContentSafetyPolicy();
    }

    public boolean isTextEnabled() {
        return textEnabled;
    }

    public void setTextEnabled(boolean textEnabled) {
        this.textEnabled = textEnabled;
    }

    public ContentSafetyAction getTextAction() {
        return textAction == null ? ContentSafetyAction.MASK : textAction;
    }

    public void setTextAction(ContentSafetyAction textAction) {
        this.textAction = textAction;
    }

    public String getTextMaskChar() {
        return textMaskChar == null || textMaskChar.isBlank() ? "*" : textMaskChar;
    }

    public void setTextMaskChar(String textMaskChar) {
        this.textMaskChar = textMaskChar;
    }

    public boolean isMediaEnabled() {
        return mediaEnabled;
    }

    public void setMediaEnabled(boolean mediaEnabled) {
        this.mediaEnabled = mediaEnabled;
    }

    public ContentSafetyAction getMediaAction() {
        return mediaAction == null ? ContentSafetyAction.SEND_THEN_REVIEW : mediaAction;
    }

    public void setMediaAction(ContentSafetyAction mediaAction) {
        this.mediaAction = mediaAction;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean isHoldFailOpen() {
        return holdFailOpen;
    }

    public void setHoldFailOpen(boolean holdFailOpen) {
        this.holdFailOpen = holdFailOpen;
    }

    public long getHoldTimeoutMs() {
        return holdTimeoutMs;
    }

    public void setHoldTimeoutMs(long holdTimeoutMs) {
        this.holdTimeoutMs = holdTimeoutMs;
    }

    public boolean isNotifySender() {
        return notifySender;
    }

    public void setNotifySender(boolean notifySender) {
        this.notifySender = notifySender;
    }

    public boolean isAutoWithdraw() {
        return autoWithdraw;
    }

    public void setAutoWithdraw(boolean autoWithdraw) {
        this.autoWithdraw = autoWithdraw;
    }

    public boolean isSystemWithdrawBypassWindow() {
        return systemWithdrawBypassWindow;
    }

    public void setSystemWithdrawBypassWindow(boolean systemWithdrawBypassWindow) {
        this.systemWithdrawBypassWindow = systemWithdrawBypassWindow;
    }
}
