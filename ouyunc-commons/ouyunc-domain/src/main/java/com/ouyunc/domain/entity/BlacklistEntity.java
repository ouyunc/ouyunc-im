package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 黑名单表
* @TableName ouyunc_im_blacklist
*/
@TableName("ouyunc_im_blacklist")
public class BlacklistEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 成为好友的时间戳， 毫秒
     */
    private Long joinTime;



    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdentity() {
        return identity;
    }

    public void setIdentity(Long identity) {
        this.identity = identity;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getIdentityType() {
        return identityType;
    }

    public void setIdentityType(Integer identityType) {
        this.identityType = identityType;
    }

    public Long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(Long joinTime) {
        this.joinTime = joinTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
