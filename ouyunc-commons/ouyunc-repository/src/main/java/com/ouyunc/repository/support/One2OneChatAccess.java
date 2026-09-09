package com.ouyunc.repository.support;

/**
 * 私聊发送准入快照：一次 Pipeline（或本地缓存）得到好友/拉黑/屏蔽。
 */
public record One2OneChatAccess(boolean friend, boolean blacklisted, boolean shielded) {

    public boolean rejectSend() {
        return !friend || blacklisted || shielded;
    }

    public String rejectReason() {
        if (!friend) {
            return "双方不是好友，无法发送消息";
        }
        if (blacklisted) {
            return "对方已拉黑，无法发送消息";
        }
        if (shielded) {
            return "对方已屏蔽，无法发送消息";
        }
        return "";
    }
}
