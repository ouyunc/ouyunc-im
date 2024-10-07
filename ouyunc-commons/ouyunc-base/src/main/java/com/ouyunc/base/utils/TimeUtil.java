package com.ouyunc.base.utils;

import org.apache.commons.lang3.time.StopWatch;

/**
 * 时间工具类
 */
public class TimeUtil {

    /**
     * 获取当前时间戳，毫秒
     * @return
     */

    public static long currentTimeMillis() {
        return SystemClock.now();
    }




    public static void main(String[] args) {

        for (int t = 0; t < 100; t++) {
            StopWatch stopWatch = StopWatch.createStarted();
            // 获取一千万次时间
            for (int i = 0; i < 10000000; i++) {
                long l = currentTimeMillis();
                //long l1 = System.currentTimeMillis();
                //System.out.println("l="+l + "         l1"+l1);
            }
            stopWatch.stop();
            System.out.println(stopWatch.getTime());
        }

    }
}
