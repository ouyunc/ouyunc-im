package com.ouyunc.core.properties;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.core.properties.annotation.Key;
import io.netty.handler.logging.LogLevel;

/**
 * @author fzx
 * @description 消息配置信息类
 */
public class MessageProperties {

    /***
     * 端口地址, 如如果是服务端则代表绑定端口，如果是客户端则代表是链接端口
     */
    @Key(value = "ouyunc.message.port", defaultValue = "8080")
    private int port;

    /***
     * 如果是服务端 ，server提供的暴露ip(可以理解为主动设置本机ip),如果不指定则使用本地网卡ip，一般为内网ip; 注意如果开启集群，要注意集群服务的地址和该服务所在ip的网络是否连通
     * client ，如果是客户端则代表的是绑定的远端ip 或者是域名
     */
    @Key(value = "ouyunc.message.ip", defaultValue = "127.0.0.1")
    private String ip;

    /**
     * 应用名称名称
     */
    @Key(value = "ouyunc.message.application-name", defaultValue = MessageConstant.DEFAULT_APPLICATION_NAME)
    private String applicationName;

    /**
     * 本地host地址，通过InetAddress.getLocalHost().getHostAddress()获取
     */
    protected String localHost;

    /**
     * 日志级别,默认INFO; TRACE, DEBUG, INFO, WARN, ERROR
     */
    @Key(value = "ouyunc.message.log.level", defaultValue = "INFO")
    private LogLevel logLevel;

    /**
     * 是否开启ssl/tls
     */
    @Key(value = "ouyunc.message.ssl.enable", defaultValue = "false")
    private boolean sslEnable;

    /**
     * ssl/tls 证书文件路径
     */
    @Key(value = "ouyunc.message.ssl.certificate", defaultValue = "ssl/m.ouyunc.com.pem")
    private String sslCertificate;

    /**
     * ssl/tls 证书私钥文件路径
     */
    @Key(value = "ouyunc.message.ssl.privateKey", defaultValue = "ssl/m.ouyunc.com_pkcs8.key")
    private String sslPrivateKey;



    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getIp() {
        return ip == null ? localHost : ip;
    }

    public String getLocalServerAddress() {
        return getIp()  + MessageConstant.COLON_SPLIT + port;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getLocalHost() {
        return localHost;
    }

    public void setLocalHost(String localHost) {
        this.localHost = localHost;
    }

    public boolean isSslEnable() {
        return sslEnable;
    }

    public void setSslEnable(boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    public String getSslCertificate() {
        return sslCertificate;
    }

    public void setSslCertificate(String sslCertificate) {
        this.sslCertificate = sslCertificate;
    }

    public String getSslPrivateKey() {
        return sslPrivateKey;
    }

    public void setSslPrivateKey(String sslPrivateKey) {
        this.sslPrivateKey = sslPrivateKey;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }



    @Override
    public String toString() {
        return "BaseMessageProperties{" +
                "port=" + port +
                ", ip='" + ip + '\'' +
                ", localHost='" + localHost + '\'' +
                ", logLevel=" + logLevel +
                ", sslEnable=" + sslEnable +
                ", sslCertificate='" + sslCertificate + '\'' +
                ", sslPrivateKey='" + sslPrivateKey + '\'' +
                '}';
    }
}
