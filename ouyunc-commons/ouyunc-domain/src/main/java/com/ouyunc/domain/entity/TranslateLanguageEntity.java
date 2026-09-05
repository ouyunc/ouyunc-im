package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支持的翻译语种目录。运维改本表即可；目录加载与 Redis 同步在 im-web，本模块只映射表结构。
 *
 * @TableName ouyunc_im_translate_language
 */
@TableName("ouyunc_im_translate_language")
public class TranslateLanguageEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规范化语种码，如 zh-Hans */
    @TableId(type = IdType.INPUT)
    private String code;

    /** 前端下拉展示名 */
    private String name;

    /** 历史别名，逗号分隔：zh,zh-cn */
    private String aliases;

    /** 机器翻译厂商语种名，如 Qwen-MT 的 Chinese */
    private String mtLang;

    /** 源语言粗检脚本：kana / hangul / han / cyrillic / latin */
    private String detectScript;

    /** 是否缺省目标语种：1 是 0 否；多条为 1 时取 sort_no 最小的 */
    private Integer defaultFlag;

    /** 下拉排序，升序 */
    private Integer sortNo;

    /** 1 启用 0 停用 */
    private Integer status;

    /** 软删除：0 未删除；非 0 为删除毫秒时间戳 */
    private Long delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public String getMtLang() {
        return mtLang;
    }

    public void setMtLang(String mtLang) {
        this.mtLang = mtLang;
    }

    public String getDetectScript() {
        return detectScript;
    }

    public void setDetectScript(String detectScript) {
        this.detectScript = detectScript;
    }

    public Integer getDefaultFlag() {
        return defaultFlag;
    }

    public void setDefaultFlag(Integer defaultFlag) {
        this.defaultFlag = defaultFlag;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Long delFlag) {
        this.delFlag = delFlag;
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
