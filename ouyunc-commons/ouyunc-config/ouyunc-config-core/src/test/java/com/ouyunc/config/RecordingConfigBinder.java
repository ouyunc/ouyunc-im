package com.ouyunc.config;

import com.ouyunc.base.config.ConfigBinder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用绑定器，确认引导安装文档后会走到模块绑定。
 */
public final class RecordingConfigBinder implements ConfigBinder {

    static final AtomicInteger CALLS = new AtomicInteger();

    @Override
    public void bind() {
        CALLS.incrementAndGet();
    }
}
