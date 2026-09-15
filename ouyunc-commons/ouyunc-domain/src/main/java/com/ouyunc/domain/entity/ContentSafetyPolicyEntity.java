package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM 内容安全租户策略表 {@code ouyunc_im_content_safety_policy}。
 * <p>{@link #jsonConfig} 对应 {@code ContentSafetyPolicy} JSON；{@link #wordVersion} 随词库推 Redis 自增。</p>
 */
@TableName("ouyunc_im_content_safety_policy")
public class ContentSafetyPolicyEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 appKey，主键。 */
    @TableId
    private String appKey;
    /** 策略 JSON，反序列化为 {@code ContentSafetyPolicy}。 */
    private String jsonConfig;
    /** 词库版本，与 Redis version key 对齐，便于排查是否推送成功。 */
    private Long wordVersion;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 修改时间。 */
    private LocalDateTime updateTime;

    /**
     * @return 租户 appKey
     */
    public String getAppKey() {
        return appKey;
    }

    /**
     * @param appKey 租户 appKey
     */
    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    /**
     * @return 策略 JSON
     */
    public String getJsonConfig() {
        return jsonConfig;
    }

    /**
     * @param jsonConfig 策略 JSON
     */
    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
    }

    /**
     * @return 词库版本
     */
    public Long getWordVersion() {
        return wordVersion;
    }

    /**
     * @param wordVersion 词库版本
     */
    public void setWordVersion(Long wordVersion) {
        this.wordVersion = wordVersion;
    }

    /**
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * @return 修改时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * @param updateTime 修改时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
