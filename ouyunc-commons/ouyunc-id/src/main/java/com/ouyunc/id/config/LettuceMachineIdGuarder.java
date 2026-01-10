package com.ouyunc.id.config;

import me.ahoo.cosid.machine.GuardianState;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.NamespacedInstanceId;

import java.util.Map;


/**
 * @author fzx
 * @description 默认的cosid机器id保护者
 */
public class LettuceMachineIdGuarder implements MachineIdGuarder {
    @Override
    public Map<NamespacedInstanceId, GuardianState> getGuardianStates() {
        return Map.of();
    }

    @Override
    public void register(String namespace, InstanceId instanceId) {

    }

    @Override
    public void unregister(String namespace, InstanceId instanceId) {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
