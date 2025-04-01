package com.ouyunc.base.constant.enums;


/**
 * disruptor事件生产者枚举
 */
public enum DisruptorEventProducerEnum {

    EXCEPTION_PRODUCER(1, "exception_producer", "异常事件生产者"),

   ;


    private Integer code;

    private String name;

    private String description;


    DisruptorEventProducerEnum(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }


    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
