package com.ouyunc.id.config;

import com.ouyunc.base.constant.NumberConstant;
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import org.jspecify.annotations.NonNull;

/**
 * 加强版雪花ID生成器
 *
 * @author ahoo wang
 * @since 1.0.0
 */
public class StrongClockSyncSnowflakeId extends ClockSyncSnowflakeId {
    public StrongClockSyncSnowflakeId(SnowflakeId actual) {
        super(actual);
    }

    public StrongClockSyncSnowflakeId(SnowflakeId actual, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        super(actual, clockBackwardsSynchronizer);
    }

    @Override
    public @NonNull IdConverter idConverter() {
        return new ToStringIdConverter(true, NumberConstant.NUMBER_19);
    }
}
