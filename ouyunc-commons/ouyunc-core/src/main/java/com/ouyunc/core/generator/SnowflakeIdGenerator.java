package com.ouyunc.core.generator;

import com.ouyunc.base.utils.SnowflakeUtil;

/**
 * 雪花id生成器
 */
public enum SnowflakeIdGenerator implements IdGenerator<Long>{
    INSTANCE
    ;

    @Override
    public Long generateId() {
        return SnowflakeUtil.nextId();
    }
}
