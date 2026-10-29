package com.ouyunc.mq.rocket.builder;

import com.ouyunc.mq.rocket.properties.RocketProperties;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.remoting.RPCHook;

/**
 * 生产者、消费者共用的接入参数，只按配置写入，不根据地址改行为。
 */
final class RocketClientSupport {

    private RocketClientSupport() {
    }

    /**
     * 未配置 accessKey 时不启用 ACL。
     */
    static RPCHook aclHook(RocketProperties rocket) {
        if (isBlank(rocket.getAccessKey())) {
            return null;
        }
        return new AclClientRPCHook(new SessionCredentials(rocket.getAccessKey(), rocket.getSecretKey()));
    }

    /**
     * 写入 NameServer。vipChannelEnabled、namespace 仅在配置了值时设置。
     */
    static void applyCommon(ClientConfig client, RocketProperties rocket, String namesrv) {
        client.setNamesrvAddr(namesrv);
        if (rocket.getVipChannelEnabled() != null) {
            client.setVipChannelEnabled(rocket.getVipChannelEnabled());
        }
        if (!isBlank(rocket.getNamespace())) {
            client.setNamespace(rocket.getNamespace());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
