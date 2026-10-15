package com.ouyunc.message.ratelimit;

import java.util.function.LongSupplier;

/**
 * 本机令牌桶。补充发生在持有者自己的锁内，业务线程不会被桶堵住。
 * 同一桶上的获取和退回必须成对出现在同一调用栈，避免消息令牌已经扣掉、字节令牌却失败。
 */
public final class LocalTokenBucket {

    private final double ratePerSecond;
    private final double burst;
    private final LongSupplier clockNanos;
    private double available;
    private long lastNanos;

    public LocalTokenBucket(double ratePerSecond, double burst) {
        this(ratePerSecond, burst, System::nanoTime);
    }

    /**
     * @param clockNanos 单调时钟，测试可注入；生产使用 {@link System#nanoTime()}
     */
    public LocalTokenBucket(double ratePerSecond, double burst, LongSupplier clockNanos) {
        if (ratePerSecond <= 0D || burst <= 0D) {
            throw new IllegalArgumentException("rate and burst must be positive");
        }
        this.ratePerSecond = ratePerSecond;
        this.burst = burst;
        this.clockNanos = clockNanos == null ? System::nanoTime : clockNanos;
        this.available = burst;
        this.lastNanos = this.clockNanos.getAsLong();
    }

    /** 令牌不足时不扣减。费用为 0 直接通过，负数拒绝。 */
    public synchronized boolean tryAcquire(double cost) {
        if (cost < 0D) {
            return false;
        }
        if (cost == 0D) {
            return true;
        }
        refill();
        if (available < cost) {
            return false;
        }
        available -= cost;
        return true;
    }

    /** 只退回刚刚扣掉的费用，退回后不超过突发容量。 */
    public synchronized void refund(double cost) {
        if (cost <= 0D) {
            return;
        }
        refill();
        available = Math.min(burst, available + cost);
    }

    private void refill() {
        long now = clockNanos.getAsLong();
        long elapsed = now - lastNanos;
        if (elapsed <= 0L) {
            return;
        }
        lastNanos = now;
        double refill = (elapsed / 1_000_000_000.0D) * ratePerSecond;
        if (refill >= burst) {
            available = burst;
            return;
        }
        available = Math.min(burst, available + refill);
    }
}
