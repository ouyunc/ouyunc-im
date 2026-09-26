package com.ouyunc.message.support;

import io.netty.util.NetUtil;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

/**
 * PROXY Protocol 可信代理地址校验。
 *
 * <p>仅信任与服务端直接建立 TCP 连接的代理地址。若不校验该地址，任意直连客户端都可以伪造
 * PROXY 头中的源 IP，污染审计日志、消息入口元数据以及依赖客户端 IP 的限流策略。</p>
 */
public final class ProxyProtocolTrustSupport {

    private static final String CIDR_SEPARATOR = "/";

    private ProxyProtocolTrustSupport() {
    }

    /**
     * 判断 TCP 对端是否命中可信代理地址或 CIDR。空名单采用拒绝策略，避免配置遗漏后意外信任公网连接。
     */
    public static boolean isTrustedProxy(SocketAddress remoteAddress, List<String> trustedProxies) {
        if (!(remoteAddress instanceof InetSocketAddress inetSocketAddress)
                || inetSocketAddress.getAddress() == null
                || trustedProxies == null
                || trustedProxies.isEmpty()) {
            return false;
        }
        byte[] remoteBytes = inetSocketAddress.getAddress().getAddress();
        for (String trustedProxy : trustedProxies) {
            if (matches(remoteBytes, trustedProxy)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(byte[] remoteBytes, String configuredAddress) {
        if (StringUtils.isBlank(configuredAddress)) {
            return false;
        }
        String value = configuredAddress.trim();
        int separatorIndex = value.indexOf(CIDR_SEPARATOR);
        String addressPart = separatorIndex >= 0 ? value.substring(0, separatorIndex) : value;
        // NetUtil 仅解析数字 IP，不会在 Netty EventLoop 上触发 DNS 查询。
        byte[] configuredBytes = NetUtil.createByteArrayFromIpAddressString(addressPart);
        if (configuredBytes == null || remoteBytes.length != configuredBytes.length) {
            return false;
        }
        try {
            int prefixLength = separatorIndex >= 0
                    ? Integer.parseInt(value.substring(separatorIndex + 1))
                    : configuredBytes.length * Byte.SIZE;
            return prefixLength >= 0
                    && prefixLength <= configuredBytes.length * Byte.SIZE
                    && matchesPrefix(remoteBytes, configuredBytes, prefixLength);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean matchesPrefix(byte[] remoteBytes, byte[] configuredBytes, int prefixLength) {
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int i = 0; i < fullBytes; i++) {
            if (remoteBytes[i] != configuredBytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (Byte.SIZE - remainingBits);
        return (remoteBytes[fullBytes] & mask) == (configuredBytes[fullBytes] & mask);
    }
}
