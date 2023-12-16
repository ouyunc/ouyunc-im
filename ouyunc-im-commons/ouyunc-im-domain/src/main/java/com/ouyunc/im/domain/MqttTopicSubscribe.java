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
@TableName("ouyunc_mqtt_topic_subscribe")
public class MqttTopicSubscribe implements Serializable {
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
     * 主题id
     */
    private Long topicId;


    /**
     * 客户端id
     */
    private String clientId;

    /**
     * qos 消息质量等级： 0,1,2
     */
    private int qos;

    /**
     * 在 MQTT v3.1.1 中，如果你订阅了自己发布消息的主题，那么你将收到自己发布的所有消息。
     * 而在 MQTT v5 中，如果你在订阅时将此选项设置为 1，那么服务端将不会向你转发你自己发布的消息;0-false, 1-true
     */
    private int noLocal;

    /**
     * 这一选项用来指定服务端向客户端转发消息时是否要保留其中的 RETAIN 标识，注意这一选项不会影响保留消息中的 RETAIN 标识。因此当 Retain As Publish 选项被设置为 0 时，客户端直接依靠消息中的 RETAIN 标识来区分这是一个正常的转发消息还是一个保留消息，而不是去判断消息是否是自己订阅后收到的第一个消息（转发消息甚至可能会先于保留消息被发送，视不同 Broker 的具体实现而定）0-false, 1-true
     */
    private int retainAsPublished;

    /**
     *  这一选项用来指定订阅建立时服务端是否向客户端发送保留消息：
     * Retain Handling 等于 0，只要客户端订阅成功，服务端就发送保留消息。
     * Retain Handling 等于 1，客户端订阅成功且该订阅此前不存在，服务端才发送保留消息。毕竟有些时候客户端重新发起订阅可能只是为了改变一下 QoS，并不意味着它想再次接收保留消息。
     * Retain Handling 等于 2，即便客户订阅成功，服务端也不会发送保留消息;
     */
    private int retainHandling;


    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 更新时间
     */
    private String updateTime;


    /**
     * 是否删除(0-否，1-是)
     */
    @TableLogic
    private Integer deleted;


    public MqttTopicSubscribe() {
    }

    public MqttTopicSubscribe(Long id, String appKey, Long topicId, String clientId, int qos, int noLocal, int retainAsPublished, int retainHandling, String createTime, String updateTime, Integer deleted) {
        this.id = id;
        this.appKey = appKey;
        this.topicId = topicId;
        this.clientId = clientId;
        this.qos = qos;
        this.noLocal = noLocal;
        this.retainAsPublished = retainAsPublished;
        this.retainHandling = retainHandling;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

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

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getNoLocal() {
        return noLocal;
    }

    public void setNoLocal(int noLocal) {
        this.noLocal = noLocal;
    }

    public int getRetainAsPublished() {
        return retainAsPublished;
    }

    public void setRetainAsPublished(int retainAsPublished) {
        this.retainAsPublished = retainAsPublished;
    }

    public int getRetainHandling() {
        return retainHandling;
    }

    public void setRetainHandling(int retainHandling) {
        this.retainHandling = retainHandling;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
