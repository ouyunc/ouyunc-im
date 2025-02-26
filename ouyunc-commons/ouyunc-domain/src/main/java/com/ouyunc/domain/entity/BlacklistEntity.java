package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 黑名单表
* @TableName ouyunc_im_blacklist
*/
public class BlacklistEntity implements Serializable {

    /**
    * 主键id
    */

    private Long id;
    /**
    * 群或客户端唯一标识
    */

    private Long identity;
    /**
    * 客户端id（被加入identity 黑名单）
    */

    private Long userId;
    /**
    * 唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识
    */

    private Integer identityType;
    /**
    * 创建时间
    */

    private LocalDateTime createTime;

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 群或客户端唯一标识
    */
    private void setIdentity(Long identity){
    this.identity = identity;
    }

    /**
    * 客户端id（被加入identity 黑名单）
    */
    private void setUserId(Long userId){
    this.userId = userId;
    }

    /**
    * 唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识
    */
    private void setIdentityType(Integer identityType){
    this.identityType = identityType;
    }


    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
    * 主键id
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 群或客户端唯一标识
    */
    private Long getIdentity(){
    return this.identity;
    }

    /**
    * 客户端id（被加入identity 黑名单）
    */
    private Long getUserId(){
    return this.userId;
    }

    /**
    * 唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识
    */
    private Integer getIdentityType(){
    return this.identityType;
    }


}
