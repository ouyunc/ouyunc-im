package com.ouyunc.base.utils;

import com.ouyunc.base.constant.NumberConstant;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 雪花算法工具类
 * 核心功能：生成分布式唯一ID，支持ID格式化（补0到19位）
 * @Author fangzhenxun
 * @Description 雪花算法工具类
 */
public class SnowflakeUtil {
    // ======================== 雪花算法核心常量（64位ID分段）========================
    /** 时间部分所占长度：41位，可支持约69年 */
    private static final int TIME_LEN = 41;
    /** 数据中心ID所占长度：5位（0-31） */
    private static final int DATA_LEN = 5;
    /** 机器ID所占长度：5位（0-31） */
    private static final int WORK_LEN = 5;
    /** 毫秒内序列所占长度：12位（0-4095） */
    private static final int SEQ_LEN = 12;

    /** 起始时间戳（2015-01-01 00:00:00），用于减少ID长度 */
    private static final long START_TIME = 1420041600000L;
    /** 时间部分左移位数：64-1（符号位）-41（时间）=22 */
    private static final int TIME_LEFT_BIT = 64 - 1 - TIME_LEN;
    /** 数据中心ID左移位数：22-5=17 */
    private static final int DATA_LEFT_BIT = TIME_LEFT_BIT - DATA_LEN;
    /** 机器ID左移位数：17-5=12 */
    private static final int WORK_LEFT_BIT = DATA_LEFT_BIT - WORK_LEN;

    // ======================== 最大值与随机数常量 ========================
    /** 数据中心ID最大值（31）：~(-1 << 5) = 0b11111 */
    private static final int DATA_MAX_NUM = ~(-1 << DATA_LEN);
    /** 机器ID最大值（31） */
    private static final int WORK_MAX_NUM = ~(-1 << WORK_LEN);
    /** 数据中心ID随机范围（32） */
    private static final int DATA_RANDOM = DATA_MAX_NUM + 1;
    /** 机器ID随机范围（32） */
    private static final int WORK_RANDOM = WORK_MAX_NUM + 1;
    /** 毫秒内序列最大值（4095） */
    private static final long SEQ_MAX_NUM = ~(-1 << SEQ_LEN);

    // ======================== 共享变量（需线程安全保护）========================
    /** 上次生成ID的时间戳（volatile保证多线程可见性） */
    private static volatile long LAST_TIME_STAMP = -1L;
    /** 上一次的毫秒内序列值（volatile保证多线程可见性） */
    private static volatile long LAST_SEQ = 0L;

    // ======================== 数据中心/机器ID（初始化一次，避免重复计算）========================
    /** 数据中心ID（0-31） */
    private static final long DATA_ID = initDataId();
    /** 机器ID（0-31） */
    private static final long WORK_ID = initWorkId();

    /**
     * 初始化数据中心ID（只执行一次）
     */
    private static long initDataId() {
        try {
            return getHostId(Inet4Address.getLocalHost().getHostName(), DATA_MAX_NUM);
        } catch (UnknownHostException e) {
            // 异常时随机生成0-31的数
            return new Random().nextInt(DATA_RANDOM);
        }
    }

    /**
     * 初始化机器ID（只执行一次）
     */
    private static long initWorkId() {
        try {
            return getHostId(Inet4Address.getLocalHost().getHostAddress(), WORK_MAX_NUM);
        } catch (UnknownHostException e) {
            // 异常时随机生成0-31的数
            return new Random().nextInt(WORK_RANDOM);
        }
    }

    /**
     * 生成雪花ID（核心方法，优化锁粒度）
     * @return 分布式唯一ID
     */
    public static long nextId() {
        long now = TimeUtil.currentTimeMillis();

        // 1. 时钟回拨检查（非核心逻辑，先执行，减少锁持有时间）
        if (now < LAST_TIME_STAMP) {
            long backTime = LAST_TIME_STAMP - now;
            // 回拨超过2秒直接抛异常，避免ID重复
            if (backTime >= 2000L) {
                throw new IllegalStateException(String.format("系统时钟回拨错误！%d 毫秒内拒绝生成雪花ID！", backTime));
            }
            // 回拨小于2秒，沿用上次时间戳
            now = LAST_TIME_STAMP;
        }

        // 2. 核心逻辑加锁（只锁定修改共享变量的部分）
        synchronized (SnowflakeUtil.class) {
            if (now == LAST_TIME_STAMP) {
                // 同一毫秒内，序列号自增（与运算保证不超过最大值）
                LAST_SEQ = (LAST_SEQ + 1) & SEQ_MAX_NUM;
                // 序列号溢出，等待下一个毫秒
                if (LAST_SEQ == 0) {
                    now = nextMillis(LAST_TIME_STAMP);
                    LAST_TIME_STAMP = now;
                }
            } else {
                // 不同毫秒，序列号重置为0
                LAST_SEQ = 0;
                LAST_TIME_STAMP = now;
            }
        }

        // 3. 拼接64位ID：时间戳段 + 数据中心段 + 机器段 + 序列号段
        return ((now - START_TIME) << TIME_LEFT_BIT)
                | (DATA_ID << DATA_LEFT_BIT)
                | (WORK_ID << WORK_LEFT_BIT)
                | LAST_SEQ;
    }

    /**
     * 生成雪花ID（字符串版本）
     * @return 字符串类型的雪花ID
     */
    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 获取下一不同毫秒的时间戳（优化：增加休眠，避免CPU空转）
     * @param lastMillis 上次生成ID的时间戳
     * @return 新的时间戳
     */
    private static long nextMillis(long lastMillis) {
        long now = TimeUtil.currentTimeMillis();
        // 循环等待直到获取到更大的时间戳
        while (now <= lastMillis) {
            // 短暂休眠100纳秒，减少CPU占用
            try {
                TimeUnit.NANOSECONDS.sleep(100);
            } catch (InterruptedException e) {
                // 恢复线程中断状态，避免状态丢失
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待下一个毫秒时线程被中断", e);
            }
            now = TimeUtil.currentTimeMillis();
        }
        return now;
    }

    /**
     * 根据字符串的字节数组求和取余，生成对应ID
     * @param s 输入字符串（主机名/主机地址）
     * @param max 最大值（DATA_MAX_NUM/WORK_MAX_NUM）
     * @return 0-max之间的整数
     */
    private static int getHostId(String s, int max) {
        if (StringUtils.isBlank(s)) {
            return new Random().nextInt(max + 1);
        }
        byte[] bytes = s.getBytes();
        int sum = 0;
        for (byte b : bytes) {
            sum += b;
        }
        return sum % (max + 1);
    }

    /**
     * 格式化long为19位字符串，前面补0
     * @param value 原始long类型ID
     * @return 19位补0后的字符串
     */
    public static String formatLong(long value) {
        return StringUtils.leftPad(String.valueOf(value), NumberConstant.NUMBER_19, '0');
    }

    /**
     * 格式化字符串类型ID为19位，前面补0（增加空值防护）
     * @param str 原始字符串ID
     * @return 19位补0后的字符串
     */
    public static String formatLong(String str) {
        if (StringUtils.isBlank(str)) {
            return StringUtils.leftPad("", NumberConstant.NUMBER_19, '0');
        }
        return StringUtils.leftPad(str, NumberConstant.NUMBER_19, '0');
    }

    // 测试方法（可选保留）
//    public static void main(String[] args) {
//        Set<Long> ids = new HashSet<>();
//        long start = TimeUtil.currentTimeMillis();
//        for (int i = 0; i < 100000; i++) {
//            long id = nextId();
//            ids.add(id);
//            // 每10000个打印一次进度
//            if (i % 10000 == 0) {
//                System.out.println("生成进度：" + i + "，当前ID：" + id);
//            }
//        }
//        long end = TimeUtil.currentTimeMillis();
//        System.out.println("共生成唯一ID[" + ids.size() + "]个，花费时间[" + (end - start) + "]毫秒");
//    }
}