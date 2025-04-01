package com.ouyunc.domain.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * 用户表
 *
 * @TableName ouyunc_im_user
 */
@Document(collection = "ouyunc_exception")
public class MongoExceptionEntity implements Serializable {

    /**
     * 主键id
     */
    @Id
    private Long id;

    /**
     * 异常数据
     */
    private String data;

    /**
     * 事件戳
     */
    private Long timestamp;

    public MongoExceptionEntity() {
    }

    public MongoExceptionEntity(Long id, String data, Long timestamp) {
        this.id = id;
        this.data = data;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
