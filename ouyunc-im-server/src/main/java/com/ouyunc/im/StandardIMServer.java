package com.ouyunc.im;

import com.ouyunc.im.banner.IMBanner;
import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.properties.IMServerProperties;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;


/**
 * @Author fangzhenxun
 * @Description: 标准IMServer 实现类
 **/
public class StandardIMServer extends AbstractIMServer{
    private static Logger log = LoggerFactory.getLogger(StandardIMServer.class);

    /**
     * @Author fangzhenxun
     * @Description
     * 如果需要扩展 serverChannelInit 或者 socketChannelInit 可以调用父类的set方法来进行扩展
     * 在该类的其他方法中设置也可以，通过继承ServerChannelInitializer或SocketChannelInitializer来初始化
     * @return com.ouyu.im.config.IMServerConfig
     */
    public StandardIMServer() {
        // 打印banner
        IMBanner.printBanner(System.out);
        //super.setSocketChannelInitializer(自己定义的类实现初始化方法);
        //super.setImClient(自己定义实现的内置客户端相关方法);
    }

    /**
     * @Author fangzhenxun
     * @Description 装载服务端配置属性
     * @return com.ouyu.im.config.IMServerConfig
     */
    @Override
    IMServerConfig loadProperties() {
        String host = null;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("获取本地host失败！{}", e.getMessage());
            e.printStackTrace();
        }
        log.info("开始装载配置文件信息...");
        IMServerProperties propertiesHelper = ConfigFactory.create(IMServerProperties.class, System.getProperties());
        IMServerConfig config = IMServerConfig.newBuilder()
                .port(propertiesHelper.port())
                .logLevel(propertiesHelper.logLevel())
                .localHost(host)
                .localServerAddress(host  + IMConstant.COLON_SPLIT + propertiesHelper.port())
                .workThreads(propertiesHelper.workThreads())
                .bossThreads(propertiesHelper.bossThreads())
                .clusterEnable(propertiesHelper.clusterEnable())
                .clusterAddress(propertiesHelper.clusterAddress())
                .clusterServerRouteStrategy(propertiesHelper.clusterServerRouteStrategy())
                .clusterSplitBrainDetectionEnable(propertiesHelper.clusterSplitBrainDetectionEnable())
                .clusterSplitBrainDetectionDelay(propertiesHelper.clusterSplitBrainDetectionDelay())
                .clusterMessageRetry(propertiesHelper.clusterMessageRetry())
                .clusterInnerClientHeartbeatInterval(propertiesHelper.clusterInnerClientHeartbeatInterval())
                .clusterInnerClientIdleReadTimeOut(propertiesHelper.clusterInnerClientIdleReadTimeOut())
                .clusterInnerClientIdleWriteTimeOut(propertiesHelper.clusterInnerClientIdleWriteTimeOut())
                .clusterInnerClientIdleReadWriteTimeOut(propertiesHelper.clusterInnerClientIdleReadWriteTimeOut())
                .clusterInnerClientChannelPoolCoreConnection(propertiesHelper.clusterInnerClientChannelPoolCoreConnection())
                .clusterInnerClientChannelPoolMaxConnection(propertiesHelper.clusterInnerClientChannelPoolMaxConnection())
                .clusterInnerClientChannelPoolMaxPendingAcquires(propertiesHelper.clusterInnerClientChannelPoolMaxPendingAcquires())
                .clusterInnerClientChannelPoolAcquireTimeoutMillis(propertiesHelper.clusterInnerClientChannelPoolAcquireTimeoutMillis())
                .clusterInnerClientHeartbeatWaitRetry(propertiesHelper.clusterInnerClientHeartbeatWaitRetry())
                .authEnable(propertiesHelper.authEnable())
                .dbEnable(propertiesHelper.dbEnable())
                .friendOnlinePushEnable(propertiesHelper.friendOnlinePushEnable())
                .sslEnable(propertiesHelper.sslEnable())
                .sslCertificate(propertiesHelper.sslCertificate())
                .sslPrivateKey(propertiesHelper.sslPrivateKey())
                .acknowledgeModeEnable(propertiesHelper.acknowledgeModeEnable())
                .readReceiptEnable(propertiesHelper.readReceiptEnable())
                .loginValidateEnable(propertiesHelper.loginValidateEnable())
                .loginMaxConnectionValidateEnable(propertiesHelper.loginMaxConnectionValidateEnable())
                .bossOptionSoBacklog(propertiesHelper.bossOptionSoBacklog())
                .bossOptionSoReuseaddr(propertiesHelper.bossOptionSoReuseaddr())
                .workerChildOptionSoKeepalive(propertiesHelper.workerChildOptionSoKeepalive())
                .workerChildOptionTcpNoDelay(propertiesHelper.workerChildOptionTcpNoDelay())
                .workerChildOptionSoReuseaddr(propertiesHelper.workerChildOptionSoReuseaddr())
                .heartBeatEnable(propertiesHelper.heartBeatEnable())
                .heartBeatTimeout(propertiesHelper.heartBeatTimeout())
                .heartBeatWaitRetry(propertiesHelper.heartBeatWaitRetry())
                .build();

        System.setProperty(IMConstant.LOCAL_ADDRESS_KEY, config.getLocalServerAddress());
        // 打印相关生效的配置参数
        log.info("当前配置参数:\r\n {} ", config);
        return config;
    }
}
