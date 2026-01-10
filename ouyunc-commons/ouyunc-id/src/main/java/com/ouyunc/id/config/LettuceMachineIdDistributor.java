package com.ouyunc.id.config;

import me.ahoo.cosid.machine.*;
import org.jspecify.annotations.NonNull;

import java.time.Duration;


/**
 * 默认的Redis 分布式ID生成器
 */
public class LettuceMachineIdDistributor implements MachineIdDistributor {
    @Override
    public void revert(String namespace, InstanceId instanceId) throws NotFoundMachineStateException {

    }

    @Override
    public void guard(String namespace, InstanceId instanceId, Duration safeGuardDuration) throws NotFoundMachineStateException, MachineIdLostException {

    }

    @Override
    @NonNull
    public MachineState distribute(String namespace, int machineBit, InstanceId instanceId, Duration safeGuardDuration) throws MachineIdOverflowException {
        return null;
    }
}
