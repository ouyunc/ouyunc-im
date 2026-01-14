package com.ouyunc.core.generator;

import com.ouyunc.base.utils.SnowflakeUtil;

/**
 * 雪花id生成器
 */
public enum SnowflakeIdGenerator implements IdGenerator{
    INSTANCE
    ;

    @Override
    public long generateId() {
        return SnowflakeUtil.nextId();
    }

    @Override
    public String generateIdStr() {
        return SnowflakeUtil.nextIdStr();
    }
}
