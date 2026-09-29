package com.ouyunc.message.helper;

import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import org.apache.commons.lang3.StringUtils;

/** 集群路由只读工具，不承担发送职责。 */
public final class ClusterRouteSupport {

    private ClusterRouteSupport() {
    }

    public static String destination(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return null;
        }
        Target target = packet.getMessage().getMetadata().getClusterRoute().getTarget();
        if (target == null || StringUtils.isBlank(target.getTargetServerAddress())) {
            return null;
        }
        return target.getTargetServerAddress();
    }
}
