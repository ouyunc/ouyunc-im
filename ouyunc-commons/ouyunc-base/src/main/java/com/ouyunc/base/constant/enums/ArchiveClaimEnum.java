package com.ouyunc.base.constant.enums;

/** 归档前幂等占位结果，禁止压缩成 boolean 丢失客户端恢复语义。 */
public enum ArchiveClaimEnum {
    READY,
    PENDING,
    CONFLICT,
    FAILED
}
