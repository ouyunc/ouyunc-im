package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
/**
 * IM 应用平台信息
 */
@TableName("ouyunc_im_app")
public class ImApp implements Serializable {
    private static final long serialVersionUID = 209;

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
     * IM 连接数 大于等于-1： -1 - 无限制，
     */
    private Integer imMaxConnections;



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

    public Integer getImMaxConnections() {
        return imMaxConnections;
    }

    public void setImMaxConnections(Integer imMaxConnections) {
        this.imMaxConnections = imMaxConnections;
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

    public ImApp() {
    }

}
