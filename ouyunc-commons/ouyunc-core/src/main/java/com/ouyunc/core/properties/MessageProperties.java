package com.ouyunc.core.properties;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.QosModeEnum;
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
    @Key(value = "ouyunc.message.ip")
    private String ip;

    /**
     * 应用名称名称
     */
    @Key(value = "ouyunc.message.application-name", defaultValue = MessageConstant.DEFAULT_APPLICATION_NAME)
    private String applicationName;

    /**
     * 本地host地址，通过InetAddress.getLocalHost().getHostAddress()获取
     */
    private String localHost;

    /**
     * Netty {@link io.netty.handler.logging.LoggingHandler} 级别（与 Log4j 根级别无关），默认 INFO。
     * 服务端对 DEBUG/TRACE 使用完整 ByteBuf hex 转储，INFO 及以上仅记录读写字节数等摘要。
     */
    @Key(value = "ouyunc.message.log.level", defaultValue = "INFO")
    private LogLevel logLevel;

    /**
     * 是否开启 QoS。默认开启，配合服务端模式对在线写出失败做有限次重试。
     */
    @Key(value = "ouyunc.message.qos.enable", defaultValue = "true")
    private boolean qosEnable;

    /**
     * CLIENT 由对端补可靠投递；SERVER 在始发节点按接收端登记重试。默认 SERVER，保证未开客户端 QoS 时在线失败仍会补发。
     */
    @Key(value = "ouyunc.message.qos.mode", defaultValue = "SERVER")
    private QosModeEnum qosMode;

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

    /**
     * 异常 message 最大长度（截断后进事件/MQ）
     */
    @Key(value = "ouyunc.message.exception.message-max-length", defaultValue = "512")
    private int exceptionMessageMaxLength;

    /**
     * SYSTEM/PIPELINE 是否发故障 MQ；关闭则仅日志（及 Persist 兜底）
     */
    @Key(value = "ouyunc.message.exception.mq-enabled", defaultValue = "true")
    private boolean exceptionMqEnabled;

    /**
     * MQ 失败后是否落 Mongo
     */
    @Key(value = "ouyunc.message.exception.persist-enabled", defaultValue = "true")
    private boolean exceptionPersistEnabled;

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
        return getIp()  + MessageConstant.COLON + port;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getLocalHost() {
        return getIp();
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

    public boolean isQosEnable() {
        return qosEnable;
    }

    public void setQosEnable(boolean qosEnable) {
        this.qosEnable = qosEnable;
    }

    public QosModeEnum getQosMode() {
        return qosMode;
    }

    public void setQosMode(QosModeEnum qosMode) {
        this.qosMode = qosMode;
    }

    public int getExceptionMessageMaxLength() {
        return exceptionMessageMaxLength <= 0 ? 512 : exceptionMessageMaxLength;
    }

    public void setExceptionMessageMaxLength(int exceptionMessageMaxLength) {
        this.exceptionMessageMaxLength = exceptionMessageMaxLength;
    }

    public boolean isExceptionMqEnabled() {
        return exceptionMqEnabled;
    }

    public void setExceptionMqEnabled(boolean exceptionMqEnabled) {
        this.exceptionMqEnabled = exceptionMqEnabled;
    }

    public boolean isExceptionPersistEnabled() {
        return exceptionPersistEnabled;
    }

    public void setExceptionPersistEnabled(boolean exceptionPersistEnabled) {
        this.exceptionPersistEnabled = exceptionPersistEnabled;
    }

    @Override
    public String toString() {
        return "MessageProperties{" +
                "port=" + port +
                ", ip='" + ip + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", localHost='" + localHost + '\'' +
                ", localServerAddress='" + getLocalServerAddress() + '\'' +
                ", logLevel=" + logLevel +
                ", qosEnable=" + qosEnable +
                ", qosMode=" + qosMode +
                ", sslEnable=" + sslEnable +
                ", sslCertificate='" + sslCertificate + '\'' +
                ", sslPrivateKey='" + sslPrivateKey + '\'' +
                ", exceptionMessageMaxLength=" + exceptionMessageMaxLength +
                ", exceptionMqEnabled=" + exceptionMqEnabled +
                ", exceptionPersistEnabled=" + exceptionPersistEnabled +
                '}';
    }
}
