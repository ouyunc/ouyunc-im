package com.ouyunc.message.schedule;

/**
 * 定时任务隔离域：业务重试与系统租约分缓存、分执行器，避免 QoS 容量淘汰误杀租约任务。
 */
public enum TimerTaskKind {
    /** QoS 等业务重试：大容量 Caffeine，可 SIZE 淘汰 */
    BUSINESS,
    /** 节点租约等系统任务：独立小表，不参与业务淘汰 */
    SYSTEM
}
