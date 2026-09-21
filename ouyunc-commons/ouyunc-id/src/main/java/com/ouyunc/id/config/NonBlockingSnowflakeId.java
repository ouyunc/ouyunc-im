package com.ouyunc.id.config;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

/**
 * IO 线程可调用的雪花核心。当前毫秒序列耗尽时快速失败，不持锁自旋等待下一毫秒。
 * 失败交给上层请求重试；不得把全局发号器替换为随机 ID 或重置 sequence 来绕过容量限制。
 */
final class NonBlockingSnowflakeId extends MillisecondSnowflakeId {
    NonBlockingSnowflakeId(int machineId) {
        super(CosId.COSID_EPOCH, IdGeneratorConstants.TIMESTAMP_BITS,
                IdGeneratorConstants.MACHINE_BITS, IdGeneratorConstants.SEQUENCE_BITS, machineId);
    }

    @Override
    protected long nextTime() {
        long now = getCurrentTime();
        if (now <= lastTimestamp) {
            // 父类已将 sequence 回绕到零；还原为最大值，下一次调用仍须跨毫秒才能发号。
            sequence = maxSequence;
            throw new IllegalStateException("CosId sequence exhausted; retry after clock advances");
        }
        return now;
    }
}
