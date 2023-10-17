package com.ouyunc.im.config;

import com.ouyunc.im.constant.IMConstant;

/**
 * @Author fangzhenxun
 * @Description: im 的公共抽象配置类
 **/
public abstract class IMConfig {

    /**
     * 默认server 端的绑定端口为6001
     */
    protected int port;

    /**
     * 默认server 端的ip,可以不指定
     */
    protected String ip;

    /**
     * 本地host地址，通过InetAddress.getLocalHost().getHostAddress()获取
     */
    protected String localHost;


    /**
     *  全局是否开启SSL/TLS, 默认否
     */
    protected boolean sslEnable;

    /**
     *  SSL/TLS 证书文件
     */

    protected String sslCertificate;

    /**
     *  SSL/TLS 私钥文件
     */
    protected String sslPrivateKey;

    public String getIp() {
        return ip == null ? localHost : ip;
    }

    public int getPort() {
        return port;
    }

    public String getLocalHost() {
        return localHost;
    }

    public String getLocalServerAddress() {
        return getIp()  + IMConstant.COLON_SPLIT + port;
    }


    public boolean isSslEnable() {
        return sslEnable;
    }

    public String getSslCertificate() {
        return sslCertificate;
    }

    public String getSslPrivateKey() {
        return sslPrivateKey;
    }
}
