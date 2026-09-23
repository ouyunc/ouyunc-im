package com.ouyunc.repository;

/**
 * 热路径入群结果。容量在群成员 / 用户加群 ZSET 上原子判定，不能只靠前置 Validator。
 */
public enum BindGroupResult {
    SUCCESS,
    ALREADY_MEMBER,
    GROUP_FULL,
    USER_GROUP_LIMIT,
    FAILED;

    public boolean accepted() {
        return this == SUCCESS || this == ALREADY_MEMBER;
    }

    public boolean capacityRejected() {
        return this == GROUP_FULL || this == USER_GROUP_LIMIT;
    }
}
