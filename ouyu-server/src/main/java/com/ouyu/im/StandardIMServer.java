package com.ouyu.im;

import com.ouyu.im.banner.IMBanner;
import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.config.PropertiesConfig;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public class StandardIMServer extends AbstractIMServer{
    private static Logger log = LoggerFactory.getLogger(StandardIMServer.class);


    // 如果需要扩展 serverChannelInit 或者 socketChannelInit 可以调用父类的set方法来进行扩展
    // 在该类的其他方法中设置也可以，通过继承ServerChannelInitializer或SocketChannelInitializer来初始化
    public StandardIMServer() {
        // 打印banner
        IMBanner.printBanner(System.out);
        //super.setSocketChannelInitializer(自己定义的类实现初始化方法);
        //super.setImClient(自己定义实现的内置客户端相关方法);
    }


    /**
     * @Author fangzhenxun
     * @Description 装载服务端配置属性
     * @param
     * @return com.ouyu.im.config.IMServerConfig
     */
    IMServerConfig loadConfigProperties() {
        // 获取配置文件中的封装类IMServerConfig.newBuilder()
        PropertiesConfig propertiesHelper = ConfigFactory.create(PropertiesConfig.class, System.getProperties());
        // 通过构建者模式进行封装返回数据，需要哪些数据然后去
        return IMServerConfig.newBuilder()
                .port(propertiesHelper.port())
                .workThreads(propertiesHelper.workThreads())
                .bossThreads(propertiesHelper.bossThreads())
                .clusterEnable(propertiesHelper.clusterEnable())
                .clusterAddress(propertiesHelper.clusterAddress())
                .clusterServerRouteStrategy(propertiesHelper.clusterServerRouteStrategy())
                .clusterServerRetry(propertiesHelper.clusterServerRetry())
                .clusterServerInitRegisterPeriod(propertiesHelper.clusterServerInitRegisterPeriod())
                .clusterServerIdleReadTimeOut(propertiesHelper.clusterServerIdleReadTimeOut())
                .clusterServerIdleWriteTimeOut(propertiesHelper.clusterServerIdleWriteTimeOut())
                .clusterServerIdleReadWriteTimeOut(propertiesHelper.clusterServerIdleReadWriteTimeOut())
                .clusterChannelPoolCoreConnection(propertiesHelper.clusterChannelPoolCoreConnection())
                .clusterChannelPoolMaxConnection(propertiesHelper.clusterChannelPoolMaxConnection())
                .sslEnable(propertiesHelper.sslEnable())
                .sslCertificate(propertiesHelper.sslCertificate())
                .sslPrivateKey(propertiesHelper.sslPrivateKey())
                .acknowledgeModeEnable(propertiesHelper.acknowledgeModeEnable())
                .bossOptionSoBacklog(propertiesHelper.bossOptionSoBacklog())
                .bossOptionSoReuseaddr(propertiesHelper.bossOptionSoReuseaddr())
                .workerChildOptionSoKeepalive(propertiesHelper.workerChildOptionSoKeepalive())
                .workerChildOptionTcpNoDelay(propertiesHelper.workerChildOptionTcpNoDelay())
                .workerChildOptionSoReuseaddr(propertiesHelper.workerChildOptionSoReuseaddr())
                .heartBeatTimeout(propertiesHelper.heartBeatTimeout())
                .build();
    }
}
