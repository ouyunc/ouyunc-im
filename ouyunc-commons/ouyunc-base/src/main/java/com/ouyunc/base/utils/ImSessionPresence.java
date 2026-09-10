package com.ouyunc.base.utils;

import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.NodeLeasePayload;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点租约 + 身份路由 HASH 的只读判定。心跳不再刷新登录 TTL 后，在线一律走这里。
 */
public final class ImSessionPresence {

    private ImSessionPresence() {
    }

    /**
     * 将节点 SET 与租约 MGET 解析为仍存活的 nodeId → 租约体。
     */
    public static Map<String, NodeLeasePayload> parseLiveLeases(List<String> nodeIds, List<String> leaseValues) {
        Map<String, NodeLeasePayload> live = new HashMap<>();
        if (nodeIds == null || nodeIds.isEmpty() || leaseValues == null) {
            return live;
        }
        for (int i = 0; i < nodeIds.size() && i < leaseValues.size(); i++) {
            String nodeId = nodeIds.get(i);
            NodeLeasePayload payload = NodeLeasePayload.parse(leaseValues.get(i));
            if (StringUtils.isBlank(nodeId) || payload == null) {
                continue;
            }
            live.put(nodeId, payload);
        }
        return live;
    }

    /**
     * 将节点 SET 与租约 MGET 结果解析为仍存活的 nodeId → epoch。
     */
    public static Map<String, Long> parseLiveNodeEpochs(List<String> nodeIds, List<String> leaseValues) {
        Map<String, Long> live = new HashMap<>();
        for (Map.Entry<String, NodeLeasePayload> entry : parseLiveLeases(nodeIds, leaseValues).entrySet()) {
            live.put(entry.getKey(), entry.getValue().getEpoch());
        }
        return live;
    }

    public static boolean isNodeEpochLive(Map<String, Long> liveEpochs, String nodeId, long epoch) {
        if (liveEpochs == null || StringUtils.isBlank(nodeId) || epoch <= 0L) {
            return false;
        }
        Long live = liveEpochs.get(nodeId);
        return live != null && live == epoch;
    }

    public static boolean isLoginLive(LoginClientInfo loginClientInfo, Map<String, Long> liveEpochs) {
        if (loginClientInfo == null || !OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
            return false;
        }
        return isNodeEpochLive(liveEpochs, loginClientInfo.getLoginServerAddress(), loginClientInfo.getNodeEpoch());
    }

    /**
     * 路由 HASH 中是否存在仍挂在活节点上的设备。
     */
    public static boolean isRouteOnline(Map<?, ?> routeHash, Map<String, Long> liveEpochs) {
        if (routeHash == null || routeHash.isEmpty()) {
            return false;
        }
        for (Object raw : routeHash.values()) {
            if (isEncodedRouteLive(raw, liveEpochs)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定设备 field 是否仍挂在活节点上。HASH field 为 deviceType 字符串。
     */
    public static boolean isDeviceRouteLive(Map<?, ?> routeHash, byte deviceType, Map<String, Long> liveEpochs) {
        if (routeHash == null || routeHash.isEmpty()) {
            return false;
        }
        String field = String.valueOf(deviceType);
        Object raw = routeHash.get(field);
        if (raw == null) {
            for (Map.Entry<?, ?> entry : routeHash.entrySet()) {
                if (entry.getKey() != null && field.equals(stringifyRoutePart(entry.getKey()))) {
                    raw = entry.getValue();
                    break;
                }
            }
        }
        return isEncodedRouteLive(raw, liveEpochs);
    }

    /**
     * 路由 HASH 中仍挂在活节点上的设备类型。
     */
    /**
     * 路由 HASH 中 epoch 已死、应惰性摘掉的设备类型。
     */
    public static Set<Byte> deadDeviceTypes(Map<?, ?> routeHash, Map<String, Long> liveEpochs) {
        Set<Byte> devices = new HashSet<>();
        if (routeHash == null || routeHash.isEmpty()) {
            return devices;
        }
        for (Map.Entry<?, ?> entry : routeHash.entrySet()) {
            if (isEncodedRouteLive(entry.getValue(), liveEpochs)) {
                continue;
            }
            Byte deviceType = parseDeviceField(entry.getKey());
            if (deviceType != null) {
                devices.add(deviceType);
            }
        }
        return devices;
    }

    public static Set<Byte> liveDeviceTypes(Map<?, ?> routeHash, Map<String, Long> liveEpochs) {
        Set<Byte> devices = new HashSet<>();
        if (routeHash == null || routeHash.isEmpty()) {
            return devices;
        }
        for (Map.Entry<?, ?> entry : routeHash.entrySet()) {
            if (!isEncodedRouteLive(entry.getValue(), liveEpochs)) {
                continue;
            }
            Byte deviceType = parseDeviceField(entry.getKey());
            if (deviceType != null) {
                devices.add(deviceType);
            }
        }
        return devices;
    }

    public static boolean isEncodedRouteLive(Object encodedRaw, Map<String, Long> liveEpochs) {
        String encoded = stringifyRoutePart(encodedRaw);
        if (StringUtils.isBlank(encoded)) {
            return false;
        }
        return isNodeEpochLive(liveEpochs, ImRouteCodec.nodeId(encoded), ImRouteCodec.epoch(encoded));
    }

    private static String stringifyRoutePart(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return raw.toString();
    }

    private static Byte parseDeviceField(Object key) {
        String field = stringifyRoutePart(key);
        if (StringUtils.isBlank(field)) {
            return null;
        }
        try {
            return Byte.valueOf(field);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
