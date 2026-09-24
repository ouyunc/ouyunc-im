package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 节点之间的路由状态。单机入站时保持空路由，不进入客户端报文。
 */
public final class ClusterRoute implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 转发意图。null 视为尚未转发。 */
    private ClusterForwardModeEnum clusterForwardMode;
    /** 投递重试次数，从 0 开始。 */
    private int currentRetry;
    /** 上一跳节点 ip:port。每转发一次改写。 */
    private String fromServerAddress;
    /** 最终接收方。targetServerAddress 是落地机，中转不得改成下一跳。 */
    private Target target;
    /** 已经走过的节点，用于选下一跳和失败回溯。 */
    private List<RoutingTable> routingTables;
    /** 本机没有连接时，按登录位置再投到新节点的次数。 */
    private int loginFollowHops;
    /** 为 true 时广播只打本机连接，不再向其他节点扇出。 */
    private boolean localBroadcastOnly;
    /** 跨节点扇出目标。正文只传一份，落地节点按此列表本机展开。单目标时为 null。 */
    private List<Target> fanoutTargets;

    @Override
    public ClusterRoute clone() {
        try {
            ClusterRoute copy = (ClusterRoute) super.clone();
            if (target != null) {
                copy.target = target.clone();
            }
            if (routingTables != null) {
                List<RoutingTable> tables = new ArrayList<>(routingTables.size());
                for (RoutingTable table : routingTables) {
                    tables.add(table.clone());
                }
                copy.routingTables = tables;
            }
            if (fanoutTargets != null) {
                List<Target> targets = new ArrayList<>(fanoutTargets.size());
                for (Target item : fanoutTargets) {
                    targets.add(item == null ? null : item.clone());
                }
                copy.fanoutTargets = targets;
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public List<RoutingTable> routingTables() {
        if (routingTables == null) {
            routingTables = new ArrayList<>();
        }
        return routingTables;
    }

    public ClusterForwardModeEnum getClusterForwardMode() { return clusterForwardMode; }
    public void setClusterForwardMode(ClusterForwardModeEnum clusterForwardMode) { this.clusterForwardMode = clusterForwardMode; }
    public int getCurrentRetry() { return currentRetry; }
    public void setCurrentRetry(int currentRetry) { this.currentRetry = currentRetry; }
    public String getFromServerAddress() { return fromServerAddress; }
    public void setFromServerAddress(String fromServerAddress) { this.fromServerAddress = fromServerAddress; }
    public Target getTarget() { return target; }
    public void setTarget(Target target) { this.target = target; }
    public List<RoutingTable> getRoutingTables() { return routingTables; }
    public void setRoutingTables(List<RoutingTable> routingTables) { this.routingTables = routingTables; }
    public int getLoginFollowHops() { return loginFollowHops; }
    public void setLoginFollowHops(int loginFollowHops) { this.loginFollowHops = loginFollowHops; }
    public boolean isLocalBroadcastOnly() { return localBroadcastOnly; }
    public void setLocalBroadcastOnly(boolean localBroadcastOnly) { this.localBroadcastOnly = localBroadcastOnly; }
    public List<Target> getFanoutTargets() { return fanoutTargets; }
    public void setFanoutTargets(List<Target> fanoutTargets) { this.fanoutTargets = fanoutTargets; }
}
