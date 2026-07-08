package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
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
// 添加联合索引：withdrawUserId和deviceType
@CompoundIndexes({
        @CompoundIndex(name = "idx_withdraw_user_id_device_type",
                def = "{'withdraw_user_id': 1, 'device_type': 1}")
})
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
    private String withdrawUserId;

    /**
     * 发送方设备类型：具体看 DeviceType,也有可能是用户自定义的
     */
    @Field("device_type")
    private Byte deviceType;

    public MessageWithdrawEntity() {
    }

    public static final class Fields {
        public static final String id = "id";
        public static final String withdrawnTime = "withdrawn_time";
        public static final String withdrawUserId = "withdraw_user_id";
        public static final String deviceType = "device_type";

    }


    public MessageWithdrawEntity(Long id, Long withdrawnTime, String withdrawUserId, Byte deviceType) {
        this.id = id;
        this.withdrawnTime = withdrawnTime;
        this.withdrawUserId = withdrawUserId;
        this.deviceType = deviceType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWithdrawnTime() {
        return withdrawnTime;
    }

    public void setWithdrawnTime(Long withdrawnTime) {
        this.withdrawnTime = withdrawnTime;
    }

    public String getWithdrawUserId() {
        return withdrawUserId;
    }

    public void setWithdrawUserId(String withdrawUserId) {
        this.withdrawUserId = withdrawUserId;
    }

    public Byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(Byte deviceType) {
        this.deviceType = deviceType;
    }

}
