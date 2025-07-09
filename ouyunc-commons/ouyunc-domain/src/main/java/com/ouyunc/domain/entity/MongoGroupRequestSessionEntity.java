package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 好友请求会话
* @TableName ouyunc_im_group_request_session
*/
@Document(collection = "ouyunc_im_group_request_session")
public class MongoGroupRequestSessionEntity implements Serializable {

    /**
    * 主键id
    */
    @Id
    private Long id;

    /**
    * 邀请方 （如果是主动加入，该字段为空）
    */
    @Field("inviter")
    @Indexed
    private String inviter;

    /**
    * 邀请方职位 （如果是主动加入，该字段为空） 对应枚举 GroupUserPost
    */
    @Field("inviter_post")
    private Integer inviterPost;

    /**
    * 加入方
    */
    @Field("joiner")
    @Indexed
    private String joiner;

    /**
     * 最后一条消息
     */
    @Field("last_message")
    private String lastMessage;


    /**
     * 加入方处理状态 GroupJoinerProcessStatus：0-待处理，1-同意邀请，2-拒绝邀请
     */
    @Field("joiner_process_status")
    @Indexed
    private Integer joinerProcessStatus;

    /**
    * 群id
    */
    @Field("group_id")
    @Indexed
    private String groupId;

    /**
     * 处理方
     */
    @Field("processor")
    @Indexed
    private String processor;


    /**
     * 处理人职位：1-群主，2-管理员  对应枚举 GroupUserPost
     */
    @Field("processor_post")
    private Integer processorPost;

    /**
     * 会话开id
     */
    @Field("session_id")
    @Indexed
    private String sessionId;

    /**
    * 会话开始时间戳，毫秒
    */
    @Field("session_begin_time")
    private Long sessionBeginTime;

    /**
     * 会话结束时间戳，毫秒
     */
    @Field("session_end_time")
    private Long sessionEndTime;

    /**
    * 好友请求会话状态：0-待处理， 1-已同意，2-已拒绝，3-已过期，4-已失效 (如果已经是好友，另一个就是重置时效状态)，5-自动同意
    */
    @Indexed
    @Field("status")
    private Integer status;



    /**
     * 会话session 加群方式：1-主动加群，2-被动加群（被邀请），3-扫码加群  ......
     */
    @Field("way")
    private Integer way;


    /**
     * 会话渠道，从哪里加入的，预留
     */
    @Field("channel")
    private Integer channel;

    /**
    * 创建时间
    */
    @Field("create_time")
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    @Field("update_time")
    private LocalDateTime updateTime;



    /**
     * 过期时间
     */
    @Field("expire_at")
    private LocalDateTime expireAt;

    public static final class Fields {
        public static final String id = "id";
        public static final String inviter = "inviter";
        public static final String joiner = "joiner";
        public static final String joinerProcessStatus = "joiner_process_status";
        public static final String processor = "processor";
        public static final String groupId = "group_id";
        public static final String processorPost = "processor_post";
        public static final String inviterPost = "inviter_post";
        public static final String lastMessage = "last_message";
        public static final String way = "way";
        public static final String channel = "channel";
        public static final String sessionId = "session_id";
        public static final String status = "status";
        public static final String expireAt = "expire_at";
        public static final String createTime = "create_time";
        public static final String updateTime = "update_time";
        public static final String sessionBeginTime = "session_begin_time";
        public static final String sessionEndTime = "session_end_time";
    }
    public MongoGroupRequestSessionEntity() {
    }

    public MongoGroupRequestSessionEntity(Long id, String lastMessage,  String inviter, Integer inviterPost, String joiner, String groupId, String processor, Integer processorPost, String sessionId, Long sessionBeginTime, Long sessionEndTime, Integer status, Integer way, LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime expireAt) {
        this.id = id;
        this.lastMessage = lastMessage;
        this.inviter = inviter;
        this.inviterPost = inviterPost;
        this.joiner = joiner;
        this.groupId = groupId;
        this.processor = processor;
        this.processorPost = processorPost;
        this.sessionId = sessionId;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.way = way;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.expireAt = expireAt;
    }

    public MongoGroupRequestSessionEntity(Long id, String lastMessage, String inviter, Integer inviterPost, String joiner, String groupId, String processor, Integer processorPost, String sessionId, Long sessionBeginTime, Long sessionEndTime, Integer status, Integer way, Integer channel, LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime expireAt) {
        this.id = id;
        this.lastMessage = lastMessage;
        this.inviter = inviter;
        this.inviterPost = inviterPost;
        this.joiner = joiner;
        this.groupId = groupId;
        this.processor = processor;
        this.processorPost = processorPost;
        this.sessionId = sessionId;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.way = way;
        this.channel = channel;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.expireAt = expireAt;
    }

    public MongoGroupRequestSessionEntity(Long id, String lastMessage,  String inviter, Integer inviterPost, String joiner, Integer joinerProcessStatus, String groupId, String processor, Integer processorPost, String sessionId, Long sessionBeginTime, Long sessionEndTime, Integer status, Integer way, Integer channel, LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime expireAt) {
        this.id = id;
        this.lastMessage = lastMessage;
        this.inviter = inviter;
        this.inviterPost = inviterPost;
        this.joiner = joiner;
        this.joinerProcessStatus = joinerProcessStatus;
        this.groupId = groupId;
        this.processor = processor;
        this.processorPost = processorPost;
        this.sessionId = sessionId;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.way = way;
        this.channel = channel;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.expireAt = expireAt;
    }

    public Integer getJoinerProcessStatus() {
        return joinerProcessStatus;
    }

    public void setJoinerProcessStatus(Integer joinerProcessStatus) {
        this.joinerProcessStatus = joinerProcessStatus;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getInviterPost() {
        return inviterPost;
    }

    public void setInviterPost(Integer inviterPost) {
        this.inviterPost = inviterPost;
    }

    public String getInviter() {
        return inviter;
    }

    public void setInviter(String inviter) {
        this.inviter = inviter;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getSessionBeginTime() {
        return sessionBeginTime;
    }

    public void setSessionBeginTime(Long sessionBeginTime) {
        this.sessionBeginTime = sessionBeginTime;
    }

    public Long getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(Long sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getWay() {
        return way;
    }

    public void setWay(Integer way) {
        this.way = way;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }
}
