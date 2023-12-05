package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 个人或群黑名单
 */
@TableName("ouyunc_im_blacklist")
public class ImBlacklist implements Serializable {
    private static final long serialVersionUID = 204;

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
     * 客户端id
     */
    private Long userId;

    /**
     * 所属平台唯一标识
     */
    private String appKey;

    /**
     * 唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识
     */
    private Integer identityType;

    /**
     * 创建时间
     */
    private String createTime;

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

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {

        this.createTime = createTime;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public ImBlacklist(Long identity, Long userId, Integer identityType, String createTime) {
        this.identity = identity;
        this.userId = userId;
        this.identityType = identityType;
        this.createTime = createTime;
    }

    public ImBlacklist(Long id, Long identity, Long userId, String appKey, Integer identityType, String createTime) {
        this.id = id;
        this.identity = identity;
        this.userId = userId;
        this.appKey = appKey;
        this.identityType = identityType;
        this.createTime = createTime;
    }

    public ImBlacklist() {
    }
}
