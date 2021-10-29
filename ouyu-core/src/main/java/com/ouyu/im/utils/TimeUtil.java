package com.ouyu.im.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @Author fangzhenxun
 * @Description: 时间工具类
 * @Version V1.0
 **/
public class TimeUtil {



    /**
     * @Author fangzhenxun
     * @Description 获取当前时间戳毫秒
     * @param
     * @return long
     */
    public static long currentTimestamp() {
        return LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }
}
