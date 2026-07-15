package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
* im 应用配置
* @TableName ouyunc_im_app
*/
@TableName("ouyunc_im_app")
public class AppEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
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
    private String userId;

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
    * 软删除：0-未删除；非0-已删除（毫秒时间戳）
    */
    private Long deleted;

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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
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

    public Long getDeleted() {
        return deleted;
    }

    public void setDeleted(Long deleted) {
        this.deleted = deleted;
    }
}
