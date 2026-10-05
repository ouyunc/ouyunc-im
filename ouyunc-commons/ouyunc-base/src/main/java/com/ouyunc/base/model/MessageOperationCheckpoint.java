package com.ouyunc.base.model;

import com.ouyunc.base.packet.Packet;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 已读/撤回的已校验执行快照。目标在首次校验后固定，恢复时不再受撤回标记和时间窗口影响。
 * applied 表示 MQ 已确认且 Redis 主操作已完成；通知失败只重试通知，不回退业务状态。
 */
public class MessageOperationCheckpoint implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Packet command;
    private List<Packet> targets;
    private String topic;
    private String scopeId;
    private String payloadHash;
    private boolean applied;

    public MessageOperationCheckpoint() { }
    public Packet getCommand() { return command; }
    public void setCommand(Packet command) { this.command = command; }
    public List<Packet> getTargets() { return targets; }
    public void setTargets(List<Packet> targets) { this.targets = targets; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getScopeId() { return scopeId; }
    public void setScopeId(String scopeId) { this.scopeId = scopeId; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public boolean isApplied() { return applied; }
    public void setApplied(boolean applied) { this.applied = applied; }
}
