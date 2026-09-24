package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 好友/群申请事件的不可变业务快照。
 * <p>该对象随 Kafka Packet 持久化，使消费者无需依赖生产节点预先写入 Redis 请求会话。</p>
 */
public class RequestEventContext implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private String requestSessionId;
    private Integer progress;
    private String inviter;
    private Integer inviterPost;
    private String joiner;
    private Integer joinerProcessStatus;
    private String groupId;
    private String processor;
    private Integer processorPost;
    private Integer way;
    private Integer channel;

    public RequestSession toFriendSession() {
        return new RequestSession(requestSessionId, progress);
    }

    public GroupRequestSession toGroupSession() {
        return new GroupRequestSession(requestSessionId, progress, inviter, inviterPost, joiner,
                joinerProcessStatus, groupId, processor, processorPost, way, channel);
    }

    @Override
    public RequestEventContext clone() {
        try {
            return (RequestEventContext) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getRequestSessionId() { return requestSessionId; }
    public void setRequestSessionId(String requestSessionId) { this.requestSessionId = requestSessionId; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getInviter() { return inviter; }
    public void setInviter(String inviter) { this.inviter = inviter; }
    public Integer getInviterPost() { return inviterPost; }
    public void setInviterPost(Integer inviterPost) { this.inviterPost = inviterPost; }
    public String getJoiner() { return joiner; }
    public void setJoiner(String joiner) { this.joiner = joiner; }
    public Integer getJoinerProcessStatus() { return joinerProcessStatus; }
    public void setJoinerProcessStatus(Integer joinerProcessStatus) { this.joinerProcessStatus = joinerProcessStatus; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }
    public Integer getProcessorPost() { return processorPost; }
    public void setProcessorPost(Integer processorPost) { this.processorPost = processorPost; }
    public Integer getWay() { return way; }
    public void setWay(Integer way) { this.way = way; }
    public Integer getChannel() { return channel; }
    public void setChannel(Integer channel) { this.channel = channel; }
}
