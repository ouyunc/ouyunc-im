package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;

/**
* 消息撤回表
* @TableName ouyunc_im_message_withdraw
*/
@TableName("ouyunc_im_message_withdraw")
@Document(collection = "ouyunc_im_message_withdraw")
public class MessageWithdrawEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
    * 主键
    */
    @Id
    private Long id;

    /**
    * 撤回时间戳，单位毫秒
    */
    @Field("withdrawn_time")
    private Long withdrawnTime;

    /**
    * 撤回人id
    */
    @Field("withdraw_user_id")
    private Long withdrawUserId;


    private void setId(Long id){
    this.id = id;
    }

    private void setWithdrawnTime(Long withdrawnTime){
    this.withdrawnTime = withdrawnTime;
    }


    private void setWithdrawUserId(Long withdrawUserId){
    this.withdrawUserId = withdrawUserId;
    }



    private Long getId(){
    return this.id;
    }


    private Long getWithdrawnTime(){
    return this.withdrawnTime;
    }


    private Long getWithdrawUserId(){
    return this.withdrawUserId;
    }

}
