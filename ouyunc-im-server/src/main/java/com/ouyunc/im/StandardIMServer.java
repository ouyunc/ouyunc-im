package com.ouyunc.im;

import com.ouyunc.im.banner.IMBanner;
import com.ouyunc.im.base.CommandLineArgs;
import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.properties.IMServerProperties;
import com.ouyunc.im.utils.Ip4Util;
import com.ouyunc.im.utils.ReflectUtils;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;


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
    IMServerConfig loadProperties(String... args) {
        // 1,首先加载配置文件中的配置信息，然后处理系统变量，最后处理命令行参数；优先级：(命令行 > 系统 > 配置文件)
        String host = Ip4Util.getLocalHost();
        IMServerProperties propertiesHelper = ConfigFactory.create(IMServerProperties.class, System.getProperties());
        IMServerConfig config = IMServerConfig.newBuilder()
                .port(propertiesHelper.port())
                .logLevel(propertiesHelper.logLevel())
                .localHost(host)
                .websocketPath(propertiesHelper.websocketPath())
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
        // 3,解析命令行参数,并组合属性
        combinedProperties(config, resolverArgs(args));
        // 打印相关生效的配置参数
        log.info("当前配置参数:\r\n {} ", config);
        return config;
    }


    /**
     * 组合属性
     */
    private void combinedProperties(IMServerConfig config, CommandLineArgs commandLineArgs) {
        commandLineArgs.getOptionNames().forEach(fieldName ->{
            Field field = ReflectUtils.findField(config.getClass(), fieldName);
            if (field != null) {
                ReflectUtils.setValueByField(field, config, commandLineArgs.getOptionValues(fieldName));
            }
        });
    }

    /**
     * 解析处理命令行参数
     * @param args
     * @return
     */
    private CommandLineArgs resolverArgs(String... args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String optionText = arg.substring(2);
                String optionName;
                String optionValue = null;
                int indexOfEqualsSign = optionText.indexOf('=');
                if (indexOfEqualsSign > -1) {
                    optionName = optionText.substring(0, indexOfEqualsSign);
                    optionValue = optionText.substring(indexOfEqualsSign + 1);
                } else {
                    optionName = optionText;
                }
                if (optionName.isEmpty()) {
                    log.error("非法命令行参数: {}" ,arg);
                    throw new IllegalArgumentException("非法命令行参数: " + arg);
                }
                commandLineArgs.addOptionArg(optionName, optionValue);
            } else {
                commandLineArgs.addNonOptionArg(arg);
            }
        }
        return commandLineArgs;
    }
}
