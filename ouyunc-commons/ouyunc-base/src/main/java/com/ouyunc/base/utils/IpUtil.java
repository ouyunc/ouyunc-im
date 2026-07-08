package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * @Author fzx
 * @Description: ip 工具类
 **/
public class IpUtil {
    private static final Logger log = LoggerFactory.getLogger(IpUtil.class);
    private static final String unknown = "unknown";

    private final static byte[][] INVALID_MACS = {
            {0x00, 0x05, 0x69},             // VMWare
            {0x00, 0x1C, 0x14},             // VMWare
            {0x00, 0x0C, 0x29},             // VMWare
            {0x00, 0x50, 0x56},             // VMWare
            {0x08, 0x00, 0x27},             // Virtualbox
            {0x0A, 0x00, 0x27},             // Virtualbox
            {0x00, 0x03, (byte) 0xFF},       // Virtual-PC
            {0x00, 0x15, 0x5D}              // Hyper-V
    };

    public static boolean isVMMac(byte[] mac) {
        if (null == mac) {
            return false;
        }
        for (byte[] invalid : INVALID_MACS) {
            if (invalid[0] == mac[0] && invalid[1] == mac[1] && invalid[2] == mac[2]) {
                return true;
            }
        }
        return false;
    }
    /**
     * 这里使用NetworkInterface 来获取本地ip ,使用InetAddress.getLocalHost().getHostAddress()来获取本地ip在window是正确的，linux不正确
     */
    public static String getLocalHost() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (!netInterface.isUp() || netInterface.isLoopback() || netInterface.isVirtual()) {
                    log.warn("{} is not a valid network interface", netInterface.getDisplayName());
                    continue;
                }
                if (isVMMac(netInterface.getHardwareAddress())) {
                    log.warn("{} is not a valid network interface", netInterface.getDisplayName());
                    continue;
                }
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    ip = addresses.nextElement();
                    if (ip.isLinkLocalAddress()) {
                        continue;
                    }
                    if (ip instanceof Inet4Address) {
                        return ip.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.error("IP地址获取失败！原因：{}" , e.getMessage());
        }
        return "";
    }



    /**
     * @Author fangzhenxun
     * @Description 字符串ip转int
     * @param strIp
     * @return int
     */
    public static int ip42Int(String strIp) {
        String[] ipArr = strIp.split("\\.");
        byte[] bytes = {(byte) (Integer.parseInt(ipArr[0])),
                (byte) (Integer.parseInt(ipArr[1])),
                (byte) (Integer.parseInt(ipArr[2])),
                (byte) (Integer.parseInt(ipArr[3]))
        };
        return (bytes[3] & 0xFF) | ((bytes[2] << 8) & 0xFF00) |  ((bytes[1] << 16) & 0xFF0000) |((bytes[0] << 24) & 0xFF000000);
    }

    /**
     * @Author fangzhenxun
     * @Description int ip 转字符串
     * @param intIp
     * @return java.lang.String
     */
    public static String int2Ip4(int intIp) {
        return new StringBuilder()
                .append(((intIp & 0xFF000000) >> 24) & 0xFF).append('.')
                .append((intIp & 0xFF0000) >> 16).append('.')
                .append((intIp & 0xFF00) >> 8).append('.')
                .append((intIp & 0xFF))
                .toString();
    }

    /***
     * @author fzx
     * @description 根据ctx上下文 获取客户端ip
     */
    public static String getIp(ChannelHandlerContext ctx) {
        // 首先尝试从代理信息中获取，如果没有在从ctx 中获取
        String clientRealIp = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP);
        if (clientRealIp != null) {
            return clientRealIp;
        }
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getAddress().getHostAddress();
        }
        return null;
    }


    /**
     * 获取用户真实IP地址，不使用request.getRemoteAddr()的原因是有可能用户使用了代理软件方式避免真实IP地址,
     * 可是，如果通过了多级反向代理的话，X-Forwarded-For的值并不止一个，而是一串IP值
     */
    public static String getIpFromHttpHeaders(HttpHeaders headers) {
        String ip = headers.get("x-forwarded-for");
        if (StringUtils.isNoneBlank(ip) && !unknown.equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            if(ip.contains(",")){
                ip = ip.split(",")[0];
            }
        }
        if (StringUtils.isEmpty(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = headers.get("Proxy-Client-IP");
        }
        if (StringUtils.isEmpty(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = headers.get("WL-Proxy-Client-IP");
        }
        if (StringUtils.isEmpty(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = headers.get("HTTP_CLIENT_IP");
        }
        if (StringUtils.isEmpty(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = headers.get("HTTP_X_FORWARDED_FOR");
        }
        if (StringUtils.isEmpty(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = headers.get("X-Real-IP");
        }
        return ip;
    }
}
