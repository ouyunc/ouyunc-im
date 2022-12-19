package com.ouyunc.im.utils;

/**
 * @Author fangzhenxun
 * @Description: ip4 工具类
 * @Version V3.0
 **/
public class Ip4Util {



    /**
     * @Author fangzhenxun
     * @Description 字符串ip转int
     * @param strIp
     * @return int
     */
    public static int ip2Int(String strIp) {
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
    public static String int2Ip(int intIp) {
        return new StringBuilder()
                .append(((intIp & 0xFF000000) >> 24) & 0xFF).append('.')
                .append((intIp & 0xFF0000) >> 16).append('.')
                .append((intIp & 0xFF00) >> 8).append('.')
                .append((intIp & 0xFF))
                .toString();
    }

    public static void main(String[] args) {
        String ip = "156.23.56.86";
        final int i = ip2Int(ip);
        System.out.println(int2Ip(-592154744));
    }
}
