package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 文件表
* @TableName ouyunc_im_file
*/
public class FileEntity implements Serializable {

    /**
    * 主键id
    */
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

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 文件原始名称
    */
    private void setFileOriginName(String fileOriginName){
    this.fileOriginName = fileOriginName;
    }

    /**
    * 文件名称，包含后缀名
    */
    private void setFileName(String fileName){
    this.fileName = fileName;
    }

    /**
    * 文件网络访问完整路径：http(s)://xxxxx
    */
    private void setFileUrl(String fileUrl){
    this.fileUrl = fileUrl;
    }

    /**
    * 文件半路径，一般为存放目录+文件名
    */
    private void setFilePath(String filePath){
    this.filePath = filePath;
    }

    /**
    * 文件类型，标识是哪种业务类型的文件：1-图片文件，2-文档文件，3-声音文件，4-视频文件，5-压缩文件，6-其他
    */
    private void setFileType(Integer fileType){
    this.fileType = fileType;
    }

    /**
    * 文件的md5
    */
    private void setFileMd5(String fileMd5){
    this.fileMd5 = fileMd5;
    }

    /**
    * 文件后缀名
    */
    private void setFileSuffix(String fileSuffix){
    this.fileSuffix = fileSuffix;
    }

    /**
    * 文件关联类型：用来标识该业务id的来源
    */
    private void setRelationType(Integer relationType){
    this.relationType = relationType;
    }

    /**
    * 文件关联id
    */
    private void setRelationId(String relationId){
    this.relationId = relationId;
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
    * 文件原始名称
    */
    private String getFileOriginName(){
    return this.fileOriginName;
    }

    /**
    * 文件名称，包含后缀名
    */
    private String getFileName(){
    return this.fileName;
    }

    /**
    * 文件网络访问完整路径：http(s)://xxxxx
    */
    private String getFileUrl(){
    return this.fileUrl;
    }

    /**
    * 文件半路径，一般为存放目录+文件名
    */
    private String getFilePath(){
    return this.filePath;
    }

    /**
    * 文件类型，标识是哪种业务类型的文件：1-图片文件，2-文档文件，3-声音文件，4-视频文件，5-压缩文件，6-其他
    */
    private Integer getFileType(){
    return this.fileType;
    }

    /**
    * 文件的md5
    */
    private String getFileMd5(){
    return this.fileMd5;
    }

    /**
    * 文件后缀名
    */
    private String getFileSuffix(){
    return this.fileSuffix;
    }

    /**
    * 文件关联类型：用来标识该业务id的来源
    */
    private Integer getRelationType(){
    return this.relationType;
    }

    /**
    * 文件关联id
    */
    private String getRelationId(){
    return this.relationId;
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
