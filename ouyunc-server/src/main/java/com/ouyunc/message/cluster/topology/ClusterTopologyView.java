package com.ouyunc.message.cluster.topology;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.message.cluster.routing.ClusterRoutingMode;
import com.ouyunc.message.cluster.routing.CrossZoneVia;
import com.ouyunc.message.properties.MessageServerProperties;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 集群分区拓扑视图：节点归属、网关、本机角色及连接/路由策略。
 */
public final class ClusterTopologyView {

    private static final Logger log = LoggerFactory.getLogger(ClusterTopologyView.class);

    private static final ClusterTopologyView FLAT = new ClusterTopologyView(
            ClusterRoutingMode.FLAT,
            CrossZoneVia.ANY,
            "",
            "",
            false,
            Maps.newHashMap(),
            Maps.newHashMap(),
            Sets.newHashSet()
    );

    private final ClusterRoutingMode routingMode;
    private final CrossZoneVia crossZoneVia;
    private final String localServerAddress;
    private final String localZoneId;
    private final boolean localGateway;
    private final Map<String, ClusterZoneInfo> zonesById;
    private final Map<String, String> nodeZoneIndex;
    private final Set<String> allGatewayNodes;

    private ClusterTopologyView(
            ClusterRoutingMode routingMode,
            CrossZoneVia crossZoneVia,
            String localServerAddress,
            String localZoneId,
            boolean localGateway,
            Map<String, ClusterZoneInfo> zonesById,
            Map<String, String> nodeZoneIndex,
            Set<String> allGatewayNodes) {
        this.routingMode = routingMode;
        this.crossZoneVia = crossZoneVia;
        this.localServerAddress = localServerAddress;
        this.localZoneId = localZoneId;
        this.localGateway = localGateway;
        this.zonesById = zonesById;
        this.nodeZoneIndex = nodeZoneIndex;
        this.allGatewayNodes = allGatewayNodes;
    }

    public static ClusterTopologyView flat() {
        return FLAT;
    }

    public static ClusterTopologyView from(MessageServerProperties properties) {
        ClusterRoutingMode routingMode = ClusterRoutingMode.from(properties.getClusterRoutingMode());
        if (!properties.isClusterEnable() || routingMode != ClusterRoutingMode.ZONE_AWARE) {
            return flat();
        }

        String localZoneId = StringUtils.trimToEmpty(properties.getClusterZoneId());
        if (StringUtils.isBlank(localZoneId)) {
            log.warn("cluster.routing.mode=zone-aware 但未配置 cluster.zone.id，回退为 flat 路由");
            return flat();
        }

        Map<String, ClusterZoneInfo> zones = parseTopologySection();
        if (zones.isEmpty()) {
            log.warn("cluster.routing.mode=zone-aware 但未配置 cluster.topology，回退为 flat 路由");
            return flat();
        }

        Map<String, String> nodeZoneIndex = new HashMap<>();
        Set<String> allGatewayNodes = new LinkedHashSet<>();
        for (ClusterZoneInfo zoneInfo : zones.values()) {
            for (String node : zoneInfo.getNodes()) {
                nodeZoneIndex.put(node, zoneInfo.getZoneId());
            }
            allGatewayNodes.addAll(zoneInfo.getGateways());
        }

        String localServerAddress = properties.getLocalServerAddress();
        if (!nodeZoneIndex.containsKey(localServerAddress)) {
            log.warn("本机地址 {} 未出现在 cluster.topology 中，回退为 flat 路由", localServerAddress);
            return flat();
        }

        String resolvedLocalZone = nodeZoneIndex.get(localServerAddress);
        if (!localZoneId.equals(resolvedLocalZone)) {
            log.warn("cluster.zone.id={} 与 topology 中本机所属分区 {} 不一致，以 topology 为准",
                    localZoneId, resolvedLocalZone);
            localZoneId = resolvedLocalZone;
        }

        CrossZoneVia crossZoneVia = CrossZoneVia.from(properties.getClusterCrossZoneVia());
        boolean localGateway = properties.isClusterZoneGateway()
                || zones.get(localZoneId).getGateways().contains(localServerAddress);

        ClusterTopologyView view = new ClusterTopologyView(
                routingMode,
                crossZoneVia,
                localServerAddress,
                localZoneId,
                localGateway,
                Collections.unmodifiableMap(zones),
                Collections.unmodifiableMap(nodeZoneIndex),
                Collections.unmodifiableSet(allGatewayNodes)
        );
        log.info("集群分区拓扑已加载: localZone={}, localGateway={}, zones={}, crossZoneVia={}",
                localZoneId, localGateway, zones.keySet(), crossZoneVia);
        return view;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ClusterZoneInfo> parseTopologySection() {
        Map<String, Object> topologySection = YmlUtil.getValue("ouyunc-server.yml", "ouyunc.message.cluster.topology", Map.class);
        if (MapUtils.isEmpty(topologySection)) {
            return Maps.newHashMap();
        }
        Map<String, ClusterZoneInfo> zones = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : topologySection.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> zoneMap)) {
                continue;
            }
            String zoneId = entry.getKey();
            Set<String> nodes = parseAddressSet(zoneMap.get("nodes"));
            Set<String> gateways = parseAddressSet(zoneMap.get("gateways"));
            if (nodes.isEmpty()) {
                log.warn("分区 {} 未配置 nodes，已跳过", zoneId);
                continue;
            }
            if (gateways.isEmpty()) {
                log.warn("分区 {} 未配置 gateways，跨区路由可能不可用", zoneId);
            }
            zones.put(zoneId, new ClusterZoneInfo(zoneId, nodes, gateways));
        }
        return zones;
    }

    private static Set<String> parseAddressSet(Object raw) {
        if (raw == null) {
            return Sets.newHashSet();
        }
        Set<String> result = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) {
                addAddress(result, item);
            }
            return result;
        }
        if (raw instanceof String text) {
            for (String part : text.split(",")) {
                addAddress(result, part);
            }
        }
        return result;
    }

    private static void addAddress(Set<String> target, Object value) {
        if (value == null) {
            return;
        }
        String address = StringUtils.trimToEmpty(String.valueOf(value));
        if (StringUtils.isNotBlank(address)) {
            target.add(address);
        }
    }

    public boolean isZoneAware() {
        return routingMode == ClusterRoutingMode.ZONE_AWARE;
    }

    public ClusterRoutingMode getRoutingMode() {
        return routingMode;
    }

    public CrossZoneVia getCrossZoneVia() {
        return crossZoneVia;
    }

    public String getLocalServerAddress() {
        return localServerAddress;
    }

    public String getLocalZoneId() {
        return localZoneId;
    }

    public boolean isLocalGateway() {
        return localGateway;
    }

    public Set<String> getZoneNodes(String zoneId) {
        ClusterZoneInfo zoneInfo = zonesById.get(zoneId);
        return zoneInfo == null ? Sets.newHashSet() : zoneInfo.getNodes();
    }

    public Set<String> getZoneGateways(String zoneId) {
        ClusterZoneInfo zoneInfo = zonesById.get(zoneId);
        return zoneInfo == null ? Sets.newHashSet() : zoneInfo.getGateways();
    }

    public String resolveZone(String nodeAddress) {
        if (StringUtils.isBlank(nodeAddress)) {
            return "";
        }
        return nodeZoneIndex.getOrDefault(nodeAddress, "");
    }

    public boolean isSameZone(String leftZoneId, String rightZoneId) {
        return StringUtils.isNotBlank(leftZoneId)
                && StringUtils.isNotBlank(rightZoneId)
                && leftZoneId.equals(rightZoneId);
    }

    public boolean isGateway(String nodeAddress) {
        return allGatewayNodes.contains(nodeAddress);
    }

    /**
     * 是否应与远端节点建立集群内置客户端连接。
     */
    public boolean shouldConnect(String remoteNodeAddress) {
        if (!isZoneAware()) {
            return true;
        }
        if (StringUtils.equals(localServerAddress, remoteNodeAddress)) {
            return false;
        }
        String remoteZoneId = resolveZone(remoteNodeAddress);
        if (isSameZone(localZoneId, remoteZoneId)) {
            return true;
        }
        if (crossZoneVia == CrossZoneVia.ANY) {
            return true;
        }
        return localGateway && isGateway(remoteNodeAddress);
    }

    public int countActiveNodesInLocalZone(Set<String> activeRemoteNodes) {
        int activeCount = 1;
        for (String node : getZoneNodes(localZoneId)) {
            if (!localServerAddress.equals(node) && activeRemoteNodes.contains(node)) {
                activeCount++;
            }
        }
        return activeCount;
    }

    public int getLocalZoneConfiguredNodeCount() {
        return getZoneNodes(localZoneId).size();
    }
}
