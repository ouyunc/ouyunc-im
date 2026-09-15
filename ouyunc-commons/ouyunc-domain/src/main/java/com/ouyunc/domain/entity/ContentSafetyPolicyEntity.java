package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM 内容安全租户策略（MySQL）；{@code jsonConfig} 对应 {@code ContentSafetyPolicy} JSON。
 */
@TableName("ouyunc_im_content_safety_policy")
public class ContentSafetyPolicyEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private String appKey;
    private String jsonConfig;
    private Long wordVersion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getJsonConfig() {
        return jsonConfig;
    }

    public void setJsonConfig(String jsonConfig) {
        this.jsonConfig = jsonConfig;
    }

    public Long getWordVersion() {
        return wordVersion;
    }

    public void setWordVersion(Long wordVersion) {
        this.wordVersion = wordVersion;
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
}
