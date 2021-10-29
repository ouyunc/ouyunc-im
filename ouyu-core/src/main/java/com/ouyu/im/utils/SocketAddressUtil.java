package com.ouyu.im.utils;

import com.ouyu.im.constant.ImConstant;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 网络地址工具类
 * @Version V1.0
 **/
public class SocketAddressUtil {


    /**
     * @Author fangzhenxun
     * @Description 将InetSocketAddress转成字符串类型
     * @param socketAddress
     * @return java.lang.String
     */
    public static String convert2HostPort(InetSocketAddress socketAddress) {
        if (socketAddress == null) {
            return null;
        }
        return socketAddress.getAddress().getHostAddress() + ImConstant.COLON_SPLIT + socketAddress.getPort();
    }

    /**
     * @Author fangzhenxun
     * @Description 字符串转InetSocketAddress
     * @param hostPort
     * @return java.net.InetSocketAddress
     */
    public static InetSocketAddress convert2SocketAddress(String hostPort) {
        if (hostPort == null) {
            return null;
        }
        final String[] hostPortArr = hostPort.trim().split(ImConstant.COLON_SPLIT);
        // 封装成网络地址,远端地址
        return new InetSocketAddress(hostPortArr[0], Integer.parseInt(hostPortArr[1]));
    }

}
