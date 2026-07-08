package com.ouyunc.base.model;

/**
 * 群请求session
 */
public class GroupRequestSession extends RequestSession{

    /**
     * 邀请方 （如果是主动加入，该字段为空）
     */
    private String inviter;

    /**
     * 邀请方职位 （如果是主动加入，该字段为空） 对应枚举 GroupUserPost
     */
    private Integer inviterPost;

    /**
     * 加入方
     */
    private String joiner;

    /**
     * 群id
     */
    private String groupId;

    /**
     * 处理方
     */
    private String processor;

    /**
     * 加入方处理状态，一般只有被邀请的时候才会有值 GroupJoinerProcessStatus：0-待处理，1-同意邀请，2-拒绝邀请
     */
    private Integer joinerProcessStatus;

    /**
     * 处理人职位：1-群主，2-管理员  对应枚举 GroupUserPost
     */
    private Integer processorPost;

    /**
     * 会话session 加群方式：1-主动加群，2-被动加群（被邀请），3-扫码加群  ......
     */
    private Integer way;


    /**
     * 加群渠道：预留字段，默认1
     */
    private Integer channel;

    public static GroupRequestSession.Builder newGroupBuilder() {
        return new GroupRequestSession.Builder();
    }

    public static class Builder {
        private String sessionId;
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

        public GroupRequestSession.Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public GroupRequestSession.Builder progress(Integer progress) {
            this.progress = progress;
            return this;
        }

        public GroupRequestSession.Builder inviter(String inviter) {
            this.inviter = inviter;
            return this;
        }

        public GroupRequestSession.Builder inviterPost(Integer inviterPost) {
            this.inviterPost = inviterPost;
            return this;
        }

        public GroupRequestSession.Builder joiner(String joiner) {
            this.joiner = joiner;
            return this;
        }

        public GroupRequestSession.Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }
        public GroupRequestSession.Builder joinerProcessStatus(Integer joinerProcessStatus) {
            this.joinerProcessStatus = joinerProcessStatus;
            return this;
        }
        public GroupRequestSession.Builder processor(String processor) {
            this.processor = processor;
            return this;
        }

        public GroupRequestSession.Builder processorPost(Integer processorPost) {
            this.processorPost = processorPost;
            return this;
        }

        public GroupRequestSession.Builder way(Integer way) {
            this.way = way;
            return this;
        }

        public GroupRequestSession.Builder channel(Integer channel) {
            this.channel = channel;
            return this;
        }

        public GroupRequestSession build() {
            return new GroupRequestSession(sessionId, progress, inviter, inviterPost, joiner, joinerProcessStatus, groupId, processor, processorPost, way,channel);
        }
    }


    public GroupRequestSession() {
    }

    public GroupRequestSession(String sessionId, Integer progress, String inviter, Integer inviterPost, String joiner, Integer joinerProcessStatus,  String groupId, String processor, Integer processorPost, Integer way, Integer channel) {
        super(sessionId, progress);
        this.inviter = inviter;
        this.inviterPost = inviterPost;
        this.joiner = joiner;
        this.joinerProcessStatus = joinerProcessStatus;
        this.groupId = groupId;
        this.processor = processor;
        this.processorPost = processorPost;
        this.way = way;
        this.channel = channel;
    }

    public String getInviter() {
        return inviter;
    }

    public void setInviter(String inviter) {
        this.inviter = inviter;
    }

    public Integer getInviterPost() {
        return inviterPost;
    }

    public void setInviterPost(Integer inviterPost) {
        this.inviterPost = inviterPost;
    }

    public String getJoiner() {
        return joiner;
    }

    public void setJoiner(String joiner) {
        this.joiner = joiner;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProcessor() {
        return processor;
    }

    public void setProcessor(String processor) {
        this.processor = processor;
    }

    public Integer getProcessorPost() {
        return processorPost;
    }

    public void setProcessorPost(Integer processorPost) {
        this.processorPost = processorPost;
    }

    public Integer getJoinerProcessStatus() {
        return joinerProcessStatus;
    }

    public void setJoinerProcessStatus(Integer joinerProcessStatus) {
        this.joinerProcessStatus = joinerProcessStatus;
    }

    public Integer getWay() {
        return way;
    }

    public void setWay(Integer way) {
        this.way = way;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }
}
