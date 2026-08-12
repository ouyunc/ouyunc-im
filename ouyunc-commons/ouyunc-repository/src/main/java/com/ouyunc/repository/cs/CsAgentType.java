package com.ouyunc.repository.cs;

/**
 * 客服坐席类型（与 CS {@code cs_agent.agent_type} / 路由 Hash {@code agentType} 对齐）。
 * <p>IM 侧独立常量，避免依赖 cs-service-api。</p>
 */
public final class CsAgentType {

    /** 人工坐席：需 IM 长连收消息 */
    public static final int HUMAN = 1;

    /** 机器人坐席：服务端接待，不走坐席长连 */
    public static final int ROBOT = 2;

    /** 虚拟客服：预留；默认按非长连处理 */
    public static final int VIRTUAL = 3;

    private CsAgentType() {
    }

    /**
     * 是否需要对 assignee 做 IM 长连推送。
     * <p>{@code null}/未知按人工，兼容历史路由未写 agentType。</p>
     */
    public static boolean requiresImLongConnection(Integer agentType) {
        if (agentType == null) {
            return true;
        }
        return agentType == HUMAN;
    }

    public static boolean isRobot(Integer agentType) {
        return agentType != null && agentType == ROBOT;
    }
}
