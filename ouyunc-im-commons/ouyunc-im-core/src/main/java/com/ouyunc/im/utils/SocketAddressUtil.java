package com.ouyunc.im.utils;

import com.ouyunc.im.constant.IMConstant;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 网络地址工具类
 * @Version V3.0
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
        return socketAddress.getAddress().getHostAddress() + IMConstant.COLON_SPLIT + socketAddress.getPort();
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
        final String[] hostPortArr = hostPort.trim().split(IMConstant.COLON_SPLIT);
        // 封装成网络地址,远端地址
        return new InetSocketAddress(hostPortArr[0], Integer.parseInt(hostPortArr[1]));
    }

}
