package com.ouyunc.id.config;

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import org.jspecify.annotations.NonNull;

/**
 * 加强版雪花ID生成器
 */
public class StrongClockSyncSnowflakeId extends ClockSyncSnowflakeId {
    /** 转换是纯函数，不依赖 Redis 或机器号。 */
    public static final IdConverter ID_CONVERTER = new ToStringIdConverter(true, IdGeneratorConstants.ID_WIDTH);
    private final MachineIdGuardian guardian;
    private boolean closed;

    public StrongClockSyncSnowflakeId(SnowflakeId actual) {
        super(actual);
        guardian = null;
    }

    public StrongClockSyncSnowflakeId(SnowflakeId actual, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(actual, clockBackwardsSynchronizer);
        guardian = null;
    }

    /** 生产配置必须绑定守护闸门。旧构造器仅保留源兼容。 */
    public StrongClockSyncSnowflakeId(SnowflakeId actual, MachineIdGuardian guardian) {
        super(actual);
        this.guardian = java.util.Objects.requireNonNull(guardian);
    }

    /**
     * 与关闭互斥；回拨时直接传播 CosId 异常，不在调用线程等待时钟恢复。
     * 使用后的二次检查会丢弃暂停期间过期的 ID，允许空洞但禁止不安全返回。
     */
    @Override
    public synchronized long generate() {
        if (closed) {
            throw new IllegalStateException("CosId generator is closed");
        }
        if (guardian != null) {
            guardian.requireActive();
        }
        // 兼容旧构造器显式传入的回拨策略；生产守护路径使用非等待核心。
        long id = guardian == null ? super.generate() : getActual().generate();
        if (guardian != null) {
            guardian.requireActive();
        }
        return id;
    }

    /** 等待已进入 generate 的调用结束，然后永久禁止新发号。 */
    public synchronized void close() {
        closed = true;
    }

    @Override
    public @NonNull IdConverter idConverter() {
        return ID_CONVERTER;
    }
}
