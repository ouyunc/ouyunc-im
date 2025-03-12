package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* im 应用配置
* @TableName ouyunc_im_app
*/
@TableName("ouyunc_im_app")
public class AppEntity implements Serializable {

    /**
    * 主键id
    */
    private Long id;

    /**
    * 客户端（外部平台）key  唯一
    */
    private String appKey;

    /**
    * 客户端 （外部平台）secret
    */
    private String appSecret;

    /**
    * 客户端 （外部平台）name
    */
    private String appName;

    /**
    * 用户id，一般是企业的账户
    */
    private Long userId;

    /**
    * IM 最大连接数 大于等于-1： -1 - 无限制，
    */
    private Long maxConnections;

    /**
    * 1-有效，2-禁用/锁定/无效
    */
    private Integer status;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    private LocalDateTime updateTime;

    /**
    * 是否删除：0-未删除，1-已删除
    */
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

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(Long maxConnections) {
        this.maxConnections = maxConnections;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
