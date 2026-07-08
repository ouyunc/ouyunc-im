package com.ouyunc.message.cluster;

import com.ouyunc.message.cluster.routing.ClusterNextHopSelector;
import com.ouyunc.message.cluster.routing.ClusterRoutingMode;
import com.ouyunc.message.cluster.routing.FlatNextHopSelector;
import com.ouyunc.message.cluster.routing.ZoneAwareNextHopSelector;
import com.ouyunc.message.cluster.topology.ClusterTopologyView;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import com.ouyunc.message.router.BacktrackMessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群分区路由与拓扑初始化。
 */
public final class ClusterBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ClusterBootstrap.class);

    private ClusterBootstrap() {
    }

    public static void initialize(MessageServerProperties properties) {
        ClusterTopologyView topologyView = ClusterTopologyView.from(properties);
        MessageServerContext.clusterTopologyView = topologyView;

        ClusterNextHopSelector nextHopSelector = topologyView.isZoneAware()
                ? new ZoneAwareNextHopSelector(topologyView)
                : FlatNextHopSelector.INSTANCE;
        MessageServerContext.messageRouter = new BacktrackMessageRouter(nextHopSelector);

        if (topologyView.isZoneAware()) {
            log.info("集群分区路由已启用: mode={}, localZone={}, gateway={}",
                    ClusterRoutingMode.ZONE_AWARE,
                    topologyView.getLocalZoneId(),
                    topologyView.isLocalGateway());
        }
    }
}
