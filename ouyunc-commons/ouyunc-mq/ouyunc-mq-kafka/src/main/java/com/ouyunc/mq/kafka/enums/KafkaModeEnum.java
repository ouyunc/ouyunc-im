package com.ouyunc.mq.kafka.enums;

/**
 * @Author fangzhenxun
 * @Description: mq 消息枚举类
 * @Version V1.0
 **/
public enum KafkaModeEnum {

    /**
     * 单例
     */
    STANDALONE("单例"),


    /**
     * 集群，
     */
    CLUSTER("集群");

    /**
     * mq的模式类型
     */
    private String mode;


    KafkaModeEnum(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
