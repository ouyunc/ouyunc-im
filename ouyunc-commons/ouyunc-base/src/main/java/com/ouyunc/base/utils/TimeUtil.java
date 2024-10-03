package com.ouyunc.base.utils;

import java.time.Clock;

/**
 * 时间工具类
 */
public class TimeUtil {

    /**
     * 获取当前时间戳，毫秒
     * @return
     */

    public static long currentTimeMillis() {
        return Clock.systemUTC().millis();
    }
}
