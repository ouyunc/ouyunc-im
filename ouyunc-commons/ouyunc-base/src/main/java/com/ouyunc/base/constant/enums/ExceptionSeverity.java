package com.ouyunc.base.constant.enums;

/**
 * 异常严重级别：决定是否进入故障 MQ。
 */
public enum ExceptionSeverity {

    /** 可预期业务拒绝（鉴权、参数、关系校验等），只打日志，不进故障 MQ */
    BUSINESS,

    /** 系统运行故障（持久化、MQ、调度、未知错误等），进故障 MQ */
    SYSTEM,

    /** 管道损坏（编解码/SSL/IO），进故障 MQ，并通常关连接 */
    PIPELINE
}
