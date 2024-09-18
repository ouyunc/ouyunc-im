package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;

import java.net.InetSocketAddress;

/**
 * @Author fzx
 * @Description: 网络地址工具类
 **/
public class SocketAddressUtil {


    /**
     * @Author fzx
     * @Description 将InetSocketAddress转成字符串类型
     * @param socketAddress
     * @return java.lang.String
     */
    public static String convert2HostPort(InetSocketAddress socketAddress) {
        if (socketAddress == null) {
            return null;
        }
        return socketAddress.getAddress().getHostAddress() + MessageConstant.COLON_SPLIT + socketAddress.getPort();
    }

    /**
     * @Author fzx
     * @Description 字符串转InetSocketAddress
     * @param hostPort
     * @return java.net.InetSocketAddress
     */
    public static InetSocketAddress convert2SocketAddress(String hostPort) {
        if (hostPort == null) {
            return null;
        }
        final String[] hostPortArr = hostPort.trim().split(MessageConstant.COLON_SPLIT);
        // 封装成网络地址,远端地址
        return new InetSocketAddress(hostPortArr[0], Integer.parseInt(hostPortArr[1]));
    }

}
