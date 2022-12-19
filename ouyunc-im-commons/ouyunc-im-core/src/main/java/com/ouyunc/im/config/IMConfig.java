package com.ouyunc.im.config;

/**
 * @Author fangzhenxun
 * @Description: im 的公共抽象配置类
 * @Version V3.0
 **/
public abstract class IMConfig {

    /**
     * 本地host地址，通过InetAddress.getLocalHost().getHostAddress()获取
     */
    protected String localHost;


    /**
     * 本地服务地址 ip:port
     */
    protected String localServerAddress;

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

    public String getLocalHost() {
        return localHost;
    }

    public String getLocalServerAddress() {
        return localServerAddress;
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
