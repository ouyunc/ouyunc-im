package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM 敏感词库表 {@code ouyunc_im_sensitive_word}。
 * <p>MySQL 为源；变更后须重建 Redis Hash 并 Pub/Sub，IM 节点才热更新。唯一键 (app_key, word)。</p>
 */
@TableName("ouyunc_im_sensitive_word")
public class SensitiveWordEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花）。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 租户 appKey；平台默认词库用 {@code __global__}。 */
    private String appKey;
    /** 敏感词原文。 */
    private String word;
    /** 分类：见 {@link com.ouyunc.base.constant.enums.SensitiveWordCategoryEnum}。 */
    private String category;
    /** 级别：1 观察、2 警告、3 拒绝倾向。 */
    private Integer level;
    /** 1 启用，0 禁用；禁用词不会推入 Redis Hash。 */
    private Integer enabled;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 修改时间。 */
    private LocalDateTime updateTime;

    /**
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

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
     * @return 敏感词
     */
    public String getWord() {
        return word;
    }

    /**
     * @param word 敏感词
     */
    public void setWord(String word) {
        this.word = word;
    }

    /**
     * @return 分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * @param category 分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * @return 级别
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * @param level 级别
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * @return 1 启用 / 0 禁用
     */
    public Integer getEnabled() {
        return enabled;
    }

    /**
     * @param enabled 1 启用 / 0 禁用
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
