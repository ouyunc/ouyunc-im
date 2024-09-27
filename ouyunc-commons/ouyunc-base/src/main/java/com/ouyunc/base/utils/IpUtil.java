package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.AttributeKey;
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


    /**
     * 这里使用NetworkInterface 来获取本地ip ,使用InetAddress.getLocalHost().getHostAddress()来获取本地ip在window是正确的，linux不正确
     */
    public static String getLocalHost() {
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = allNetInterfaces.nextElement();
                if (!(netInterface.isLoopback() || netInterface.isVirtual() || !netInterface.isUp())) {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ip = addresses.nextElement();
                        if (ip instanceof Inet4Address) {
                            return ip.getHostAddress();
                        }
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
        AttributeKey<HAProxyMessage> proxyMessageKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP);
        HAProxyMessage haproxyMsg = ctx.channel().attr(proxyMessageKey).get();
        if (haproxyMsg != null) {
            return haproxyMsg.sourceAddress();
        }
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getAddress().getHostAddress();
        }
        return null;
    }

    public static void main(String[] args) {
        String ipAddress = getLocalHost();
        System.out.println(ipAddress);
        String ip = "156.23.56.86";
        final int i = ip42Int(ip);
        System.out.println(int2Ip4(-592154744));
    }
}
