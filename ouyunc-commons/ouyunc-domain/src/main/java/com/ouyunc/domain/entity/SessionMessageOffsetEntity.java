package com.ouyunc.domain.entity;

import java.io.Serializable;

/**
* ouyunc_im_group_user 或 ouyunc_im_friend 会话消息偏移量
建议使用该方式进行频繁的更新
INSERT INTO ouyunc_im_session_message_offset (`from`, `to`, `type`, `session_message_offset`)
VALUES (?, ?, ?, ?)
ON DUPLICATE KEY UPDATE session_message_offset = VALUES(session_message_offset);
* @TableName ouyunc_im_session_message_offset
*/
public class SessionMessageOffsetEntity implements Serializable {

    /**
    * 发送方ID
    */
    private Long from;

    /**
    * 接收方ID（用户或群组）
    */
    private Long to;

    /**
    * 会话类型：1-一对一，2-群, 具体看IdentityType
    */
    private Integer type;

    /**
    * 会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取
    */
    private Long sessionMessageOffset;

    /**
    * 发送方ID
    */
    private void setFrom(Long from){
    this.from = from;
    }

    /**
    * 接收方ID（用户或群组）
    */
    private void setTo(Long to){
    this.to = to;
    }

    /**
    * 会话类型：1-一对一，2-群
    */
    private void setType(Integer type){
    this.type = type;
    }

    /**
    * 会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取
    */
    private void setSessionMessageOffset(Long sessionMessageOffset){
    this.sessionMessageOffset = sessionMessageOffset;
    }


    /**
    * 发送方ID
    */
    private Long getFrom(){
    return this.from;
    }

    /**
    * 接收方ID（用户或群组）
    */
    private Long getTo(){
    return this.to;
    }

    /**
    * 会话类型：1-一对一，2-群
    */
    private Integer getType(){
    return this.type;
    }

    /**
    * 会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取
    */
    private Long getSessionMessageOffset(){
    return this.sessionMessageOffset;
    }

}
