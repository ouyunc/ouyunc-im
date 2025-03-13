package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 文件表
* @TableName ouyunc_im_file
*/
@TableName("ouyunc_im_file")
public class FileEntity implements Serializable {

    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
    * 文件原始名称
    */
    private String fileOriginName;

    /**
    * 文件名称，包含后缀名
    */
    private String fileName;

    /**
    * 文件网络访问完整路径：http(s)://xxxxx
    */
    private String fileUrl;

    /**
    * 文件半路径，一般为存放目录+文件名
    */
    private String filePath;

    /**
    * 文件类型，标识是哪种业务类型的文件：1-图片文件，2-文档文件，3-声音文件，4-视频文件，5-压缩文件，6-其他
    */
    private Integer fileType;

    /**
    * 文件的md5
    */
    private String fileMd5;

    /**
    * 文件后缀名
    */
    private String fileSuffix;

    /**
    * 文件关联类型：用来标识该业务id的来源
    */
    private Integer relationType;

    /**
    * 文件关联id
    */
    private String relationId;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileOriginName() {
        return fileOriginName;
    }

    public void setFileOriginName(String fileOriginName) {
        this.fileOriginName = fileOriginName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Integer getFileType() {
        return fileType;
    }

    public void setFileType(Integer fileType) {
        this.fileType = fileType;
    }

    public String getFileMd5() {
        return fileMd5;
    }

    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }

    public String getFileSuffix() {
        return fileSuffix;
    }

    public void setFileSuffix(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    public Integer getRelationType() {
        return relationType;
    }

    public void setRelationType(Integer relationType) {
        this.relationType = relationType;
    }

    public String getRelationId() {
        return relationId;
    }

    public void setRelationId(String relationId) {
        this.relationId = relationId;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
