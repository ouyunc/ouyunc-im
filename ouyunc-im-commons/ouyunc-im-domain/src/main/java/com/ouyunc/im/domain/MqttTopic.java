package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: mqtt 主题
 **/
@TableName("ouyunc_mqtt_topic")
public class MqttTopic implements Serializable {
    private static final long serialVersionUID = 207;


    /**
     * 消息id，对应packetId
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属平台唯一标识
     */
    private String appKey;


    /**
     * 主题
     */
    private String topic;


    /**
     * 主题描述
     */
    private String description;

    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 是否删除(0-否，1-是)
     */
    @TableLogic
    private Integer deleted;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public MqttTopic() {
    }

    public MqttTopic(Long id, String appKey, String topic, String description, String createTime, Integer deleted) {
        this.id = id;
        this.appKey = appKey;
        this.topic = topic;
        this.description = description;
        this.createTime = createTime;
        this.deleted = deleted;
    }
}
