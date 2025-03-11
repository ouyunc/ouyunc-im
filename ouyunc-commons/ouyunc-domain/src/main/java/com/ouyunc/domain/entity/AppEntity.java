package com.ouyunc.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* im 应用配置
* @TableName ouyunc_im_app
*/
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
    private Long imMaxConnections;

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

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 客户端（外部平台）key  唯一
    */
    private void setAppKey(String appKey){
    this.appKey = appKey;
    }

    /**
    * 客户端 （外部平台）secret
    */
    private void setAppSecret(String appSecret){
    this.appSecret = appSecret;
    }

    /**
    * 客户端 （外部平台）name
    */
    private void setAppName(String appName){
    this.appName = appName;
    }

    /**
    * 用户id，一般是企业的账户
    */
    private void setUserId(Long userId){
    this.userId = userId;
    }

    /**
    * IM 最大连接数 大于等于-1： -1 - 无限制，
    */
    private void setImMaxConnections(Long imMaxConnections){
    this.imMaxConnections = imMaxConnections;
    }

    /**
    * 1-有效，2-禁用/锁定/无效
    */
    private void setStatus(Integer status){
    this.status = status;
    }



    /**
    * 是否删除：0-未删除，1-已删除
    */
    private void setDeleted(Integer deleted){
    this.deleted = deleted;
    }


    /**
    * 主键id
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 客户端（外部平台）key  唯一
    */
    private String getAppKey(){
    return this.appKey;
    }

    /**
    * 客户端 （外部平台）secret
    */
    private String getAppSecret(){
    return this.appSecret;
    }

    /**
    * 客户端 （外部平台）name
    */
    private String getAppName(){
    return this.appName;
    }

    /**
    * 用户id，一般是企业的账户
    */
    private Long getUserId(){
    return this.userId;
    }

    /**
    * IM 最大连接数 大于等于-1： -1 - 无限制，
    */
    private Long getImMaxConnections(){
    return this.imMaxConnections;
    }

    /**
    * 1-有效，2-禁用/锁定/无效
    */
    public Integer getStatus(){
        return this.status;
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

    /**
    * 是否删除：0-未删除，1-已删除
    */
    private Integer getDeleted(){
    return this.deleted;
    }

}
