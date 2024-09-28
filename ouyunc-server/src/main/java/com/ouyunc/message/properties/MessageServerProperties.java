package com.ouyunc.message.properties;

import com.ouyunc.core.properties.MessageProperties;
import com.ouyunc.core.properties.annotation.Key;
import com.ouyunc.core.properties.annotation.LoadProperties;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;

import java.util.*;

/**
 * @author fzx
 * @description 消息服务属性配置类
 */
@LoadProperties(sources = "ouyunc-server.yml")
public class MessageServerProperties extends MessageProperties {

    /**
     * 消息监听器扫描包路径
     */
    @Key("ouyunc.message.listeners.scan-package-paths")
    private List<String> messageListenersScanPackagePaths;

    /**
     * 消息处理器扫描包路径
     */
    @Key("ouyunc.message.processor.scan-package-paths")
    private List<String> messageProcessorScanPackagePaths;

    /**
     * 消息分发协议处理器扫描包路径
     */
    @Key("ouyunc.message.protocol-dispatcher-processor.scan-package-paths")
    private List<String> messageProtocolProcessorScanPackagePaths;


    /**
     * boss 线程组个数,默认与netty保持一致
     */
    @Key(value = "ouyunc.message.boss.threads", defaultValue = "1")
    private int bossThreads;

    /**
     * 连接超时时间, 连接超时毫秒数，默认值30000毫秒即30秒。
     */
    @Key(value = "ouyunc.message.boss.option.connect-timeout-millis", defaultValue = "30000")
    private int bossOptionConnectTimeoutMillis;

    /**
     * 指定了内核为此套接口排队的最大连接个数。对于给定的监听套接口，内核要维护两个队列:
     * 已连接队列：已完成连接队列三次握手已完成，内核正等待进程执行accept的调用中的数量
     * 未连接队列：未完成连接队列一个SYN已经到达，但三次握手还没有完成的连接中的数量
     */
    @Key(value = "ouyunc.message.boss.option.so-backlog", defaultValue = "1024")
    int bossOptionSoBacklog;

    /**
     * 地址复用，默认值true
     */
    @Key(value = "ouyunc.message.boss.option.so-reuseaddr", defaultValue = "true")
    boolean bossOptionSoReuseaddr;


    /**
     * work 线程组个数，默认与netty保持一致
     */
    @Key(value = "ouyunc.message.work.threads", defaultValue = "8")
    private int workThreads;

    /**
     * 设置连接活动保持连接状态
     */
    @Key(value = "ouyunc.message.work.child-option.so-keepalive", defaultValue = "false")
    boolean workerChildOptionSoKeepalive;

    /**
     * 激活或者禁止TCP_NODELAY套接字选项，它决定了是否使用Nagle算法。如果是时延敏感型的应用，建议关闭Nagle算法。
     */
    @Key(value = "ouyunc.message.work.child-option.tcp-no-delay", defaultValue = "true")
    boolean workerChildOptionTcpNoDelay;

    /**
     * 地址复用，默认值true
     */
    @Key(value = "ouyunc.message.work.child-option.so-reuseaddr", defaultValue = "true")
    boolean workerChildOptionSoReuseaddr;

    /**
     * 高水位 默认64kb，写高水位标记，默认值64KB(64 * 1024)。如果Netty的写缓冲区中的字节超过该值，Channel的isWritable()返回False。每个连接一个，所以不能设太大
     */
    @Key(value = "ouyunc.message.work.child-option.write-buffer-high-water-mark", defaultValue = "65536")
    int workerChildOptionWriteBufferHighWaterMark;

    /**
     * 低水位,默认32kb,写低水位标记，默认值32KB(32 * 1024)。当Netty的写缓冲区中的字节超过高水位之后若下降到低水位，则Channel的isWritable()返回True。写高低水位标记使用户可以控制写入数据速度，从而实现流量控制。推荐做法是：每次调用channl.write(msg)方法首先调用channel.isWritable()判断是否可写
     */
    @Key(value = "ouyunc.message.work.child-option.write-buffer-low-water-mark", defaultValue = "3624")
    int workerChildOptionWriteBufferLowWaterMark;


    /***
     * 全局是否开启心跳，用来检测连接上的客户端需要发送心跳包（只针对外部客户端），默认开启
     */
    @Key(value = "ouyunc.message.heart-beat.enable", defaultValue = "true")
    boolean heartBeatEnable;

    /***
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    @Key(value = "ouyunc.message.heart-beat.timeout", defaultValue = "15")
    int heartBeatTimeout;

    /***
     * 外部客户端，心跳重试等待次数，默认3次，超过3次没有心跳则关闭外部客户端channel  不能为负数
     */
    @Key(value = "ouyunc.message.heart-beat.wait-retry", defaultValue = "3")
    int heartBeatWaitRetry;




    /**
     * 服务端连接websocket的path
     */
    @Key(value = "ouyunc.message.websocket.path")
    private String websocketPath;



    /**
     * 是否开启集群，默认否
     */
    @Key(value = "ouyunc.message.cluster.enable", defaultValue = "false")
    private boolean clusterEnable;

    /**
     * # 集群中的服务ip + port (包括自己本身的ip + port), 例如：有10 台服务做集群，就把十台的服务端的IP以及端口号写上即可
     */
    @Key(value = "ouyunc.message.cluster.nodes")
    private Set<String> nodes = new HashSet<>();

    /**
     * 集群中消息重试次数，消息如果不通，会进行重试三次
     */
    @Key(value = "ouyunc.message.cluster.message-retry", defaultValue = "false")
    int clusterMessageRetry;


    /**
     * 集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认5秒
     */
    @Key(value = "ouyunc.message.cluster.client.heart-beat-interval", defaultValue = "5")
    int clusterClientHeartbeatInterval;


    /**
     * 集群中，内置客户端读超时，单位秒，默认0秒钟
     */
    @Key(value = "ouyunc.message.cluster.client.idle-read-timeout", defaultValue = "0")
    int clusterClientIdleReadTimeout;

    /**
     * 集群中，内置客户端写超时，单位秒，默认0秒钟
     */
    @Key(value = "ouyunc.message.cluster.client.idle-write-timeout", defaultValue = "0")
    int clusterClientIdleWriteTimeout;

    /**
     * 集群中，内置客户端读写超时，单位秒，默认5秒钟
     */
    @Key(value = "ouyunc.message.cluster.client.idle-read-write-timeout", defaultValue = "5")
    int clusterClientIdleReadWriteTimeout;

    /**
     * 集群中客户端channel pool 中，等待连接池连接的最大时间，单位毫秒, 默认10s
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-acquire-timeout-millis", defaultValue = "10000")
    private long clusterClientChannelPoolAcquireTimeoutMillis;

    /**
     * 集群中客户端channel pool 中最大连接数, 默认100，根据实际并发进行调整
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-max-connection", defaultValue = "100")
    private int clusterClientChannelPoolMaxConnection;

    /**
     * 集群中客户端channel pool 中，在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
     * 默认推荐 最大连接数的30%
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-max-pending-acquires", defaultValue = "30")
    private int clusterClientChannelPoolMaxPendingAcquires;

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-core-connection", defaultValue = "5")
    private int clusterClientChannelPoolCoreConnection;


    /**
     * 集群中内部客户端，发送syn最大等待3个心跳时间段，如果没有及时得到响应则进行本地下线
     */
    @Key(value = "ouyunc.message.cluster.client.heart-beat-wait-retry", defaultValue = "3")
    private int clusterClientHeartbeatWaitRetry;


    /**
     * 集群中的服务开启脑裂检测，默认开启
     */
    @Key(value = "ouyunc.message.cluster.server.split-brain-detection.enable", defaultValue = "true")
    private boolean clusterSplitBrainDetectionEnable;

    /**
     * 单位分钟，默认10 集群中，开始检测脑裂的延迟时间（服务启动后多久开始进行脑裂的检测），之后就每个心跳检测一次
     */
    @Key(value = "ouyunc.message.cluster.server.split-brain-detection.delay-time", defaultValue = "10")
    private long clusterSplitBrainDetectionDelayTime;



    public Set<String> getNodes() {
        return nodes;
    }

    public void setNodes(Set<String> nodes) {
        this.nodes = nodes;
    }

    public int getClusterMessageRetry() {
        return clusterMessageRetry;
    }

    public void setClusterMessageRetry(int clusterMessageRetry) {
        this.clusterMessageRetry = clusterMessageRetry;
    }

    public int getClusterClientHeartbeatInterval() {
        return clusterClientHeartbeatInterval;
    }

    public void setClusterClientHeartbeatInterval(int clusterClientHeartbeatInterval) {
        this.clusterClientHeartbeatInterval = clusterClientHeartbeatInterval;
    }

    public int getClusterClientIdleReadTimeout() {
        return clusterClientIdleReadTimeout;
    }

    public void setClusterClientIdleReadTimeout(int clusterClientIdleReadTimeout) {
        this.clusterClientIdleReadTimeout = clusterClientIdleReadTimeout;
    }

    public int getClusterClientIdleWriteTimeout() {
        return clusterClientIdleWriteTimeout;
    }

    public void setClusterClientIdleWriteTimeout(int clusterClientIdleWriteTimeout) {
        this.clusterClientIdleWriteTimeout = clusterClientIdleWriteTimeout;
    }

    public int getClusterClientIdleReadWriteTimeout() {
        return clusterClientIdleReadWriteTimeout;
    }

    public void setClusterClientIdleReadWriteTimeout(int clusterClientIdleReadWriteTimeout) {
        this.clusterClientIdleReadWriteTimeout = clusterClientIdleReadWriteTimeout;
    }

    public long getClusterClientChannelPoolAcquireTimeoutMillis() {
        return clusterClientChannelPoolAcquireTimeoutMillis;
    }

    public void setClusterClientChannelPoolAcquireTimeoutMillis(long clusterClientChannelPoolAcquireTimeoutMillis) {
        this.clusterClientChannelPoolAcquireTimeoutMillis = clusterClientChannelPoolAcquireTimeoutMillis;
    }

    public int getClusterClientChannelPoolMaxConnection() {
        return clusterClientChannelPoolMaxConnection;
    }

    public void setClusterClientChannelPoolMaxConnection(int clusterClientChannelPoolMaxConnection) {
        this.clusterClientChannelPoolMaxConnection = clusterClientChannelPoolMaxConnection;
    }

    public int getClusterClientChannelPoolMaxPendingAcquires() {
        return clusterClientChannelPoolMaxPendingAcquires;
    }

    public void setClusterClientChannelPoolMaxPendingAcquires(int clusterClientChannelPoolMaxPendingAcquires) {
        this.clusterClientChannelPoolMaxPendingAcquires = clusterClientChannelPoolMaxPendingAcquires;
    }

    public int getClusterClientChannelPoolCoreConnection() {
        return clusterClientChannelPoolCoreConnection;
    }

    public void setClusterClientChannelPoolCoreConnection(int clusterClientChannelPoolCoreConnection) {
        this.clusterClientChannelPoolCoreConnection = clusterClientChannelPoolCoreConnection;
    }

    public List<String> getMessageListenersScanPackagePaths() {
        return messageListenersScanPackagePaths;
    }

    public void setMessageListenersScanPackagePaths(List<String> messageListenersScanPackagePaths) {
        this.messageListenersScanPackagePaths = messageListenersScanPackagePaths;
    }

    public List<String> getMessageProcessorScanPackagePaths() {
        return messageProcessorScanPackagePaths;
    }

    public void setMessageProcessorScanPackagePaths(List<String> messageProcessorScanPackagePaths) {
        this.messageProcessorScanPackagePaths = messageProcessorScanPackagePaths;
    }

    public List<String> getMessageProtocolProcessorScanPackagePaths() {
        return messageProtocolProcessorScanPackagePaths;
    }

    public void setMessageProtocolProcessorScanPackagePaths(List<String> messageProtocolProcessorScanPackagePaths) {
        this.messageProtocolProcessorScanPackagePaths = messageProtocolProcessorScanPackagePaths;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkThreads() {
        return workThreads;
    }

    public void setWorkThreads(int workThreads) {
        this.workThreads = workThreads;
    }

    public boolean isClusterEnable() {
        return clusterEnable;
    }

    public void setClusterEnable(boolean clusterEnable) {
        this.clusterEnable = clusterEnable;
    }

    public String getWebsocketPath() {
        return websocketPath;
    }

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public boolean isClusterSplitBrainDetectionEnable() {
        return clusterSplitBrainDetectionEnable;
    }

    public void setClusterSplitBrainDetectionEnable(boolean clusterSplitBrainDetectionEnable) {
        this.clusterSplitBrainDetectionEnable = clusterSplitBrainDetectionEnable;
    }

    public long getClusterSplitBrainDetectionDelayTime() {
        return clusterSplitBrainDetectionDelayTime;
    }

    public void setClusterSplitBrainDetectionDelayTime(long clusterSplitBrainDetectionDelayTime) {
        this.clusterSplitBrainDetectionDelayTime = clusterSplitBrainDetectionDelayTime;
    }

    public int getClusterClientHeartbeatWaitRetry() {
        return clusterClientHeartbeatWaitRetry;
    }

    public void setClusterClientHeartbeatWaitRetry(int clusterClientHeartbeatWaitRetry) {
        this.clusterClientHeartbeatWaitRetry = clusterClientHeartbeatWaitRetry;
    }

    /**
     * 获取boss 线程组配置, 这里对其进行组装
     */
    @SuppressWarnings("rawtypes")
    public Map<ChannelOption, Object> getChannelOptionMap() {
        return new HashMap<>() {{
            put(ChannelOption.CONNECT_TIMEOUT_MILLIS, getBossOptionConnectTimeoutMillis());
            put(ChannelOption.SO_BACKLOG, getBossOptionSoBacklog());
            put(ChannelOption.SO_REUSEADDR, isBossOptionSoReuseaddr());
        }};
    }

    @SuppressWarnings("rawtypes")
    public Map<ChannelOption, Object> getChildChannelOptionMap() {
        return new HashMap<>() {{
            put(ChannelOption.SO_KEEPALIVE, isWorkerChildOptionSoKeepalive());
            put(ChannelOption.TCP_NODELAY, isWorkerChildOptionTcpNoDelay());
            put(ChannelOption.SO_REUSEADDR, isWorkerChildOptionSoReuseaddr());
            put(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(getWorkerChildOptionWriteBufferLowWaterMark(), getWorkerChildOptionWriteBufferHighWaterMark()));
        }};
    }



    public int getBossOptionConnectTimeoutMillis() {
        return bossOptionConnectTimeoutMillis;
    }

    public void setBossOptionConnectTimeoutMillis(int bossOptionConnectTimeoutMillis) {
        this.bossOptionConnectTimeoutMillis = bossOptionConnectTimeoutMillis;
    }

    public int getBossOptionSoBacklog() {
        return bossOptionSoBacklog;
    }

    public void setBossOptionSoBacklog(int bossOptionSoBacklog) {
        this.bossOptionSoBacklog = bossOptionSoBacklog;
    }

    public boolean isBossOptionSoReuseaddr() {
        return bossOptionSoReuseaddr;
    }

    public void setBossOptionSoReuseaddr(boolean bossOptionSoReuseaddr) {
        this.bossOptionSoReuseaddr = bossOptionSoReuseaddr;
    }

    public boolean isWorkerChildOptionSoKeepalive() {
        return workerChildOptionSoKeepalive;
    }

    public void setWorkerChildOptionSoKeepalive(boolean workerChildOptionSoKeepalive) {
        this.workerChildOptionSoKeepalive = workerChildOptionSoKeepalive;
    }

    public boolean isWorkerChildOptionTcpNoDelay() {
        return workerChildOptionTcpNoDelay;
    }

    public void setWorkerChildOptionTcpNoDelay(boolean workerChildOptionTcpNoDelay) {
        this.workerChildOptionTcpNoDelay = workerChildOptionTcpNoDelay;
    }

    public boolean isWorkerChildOptionSoReuseaddr() {
        return workerChildOptionSoReuseaddr;
    }

    public void setWorkerChildOptionSoReuseaddr(boolean workerChildOptionSoReuseaddr) {
        this.workerChildOptionSoReuseaddr = workerChildOptionSoReuseaddr;
    }

    public int getWorkerChildOptionWriteBufferHighWaterMark() {
        return workerChildOptionWriteBufferHighWaterMark;
    }

    public void setWorkerChildOptionWriteBufferHighWaterMark(int workerChildOptionWriteBufferHighWaterMark) {
        this.workerChildOptionWriteBufferHighWaterMark = workerChildOptionWriteBufferHighWaterMark;
    }

    public int getWorkerChildOptionWriteBufferLowWaterMark() {
        return workerChildOptionWriteBufferLowWaterMark;
    }

    public void setWorkerChildOptionWriteBufferLowWaterMark(int workerChildOptionWriteBufferLowWaterMark) {
        this.workerChildOptionWriteBufferLowWaterMark = workerChildOptionWriteBufferLowWaterMark;
    }

    public boolean isHeartBeatEnable() {
        return heartBeatEnable;
    }

    public void setHeartBeatEnable(boolean heartBeatEnable) {
        this.heartBeatEnable = heartBeatEnable;
    }

    public int getHeartBeatTimeout() {
        return heartBeatTimeout;
    }

    public void setHeartBeatTimeout(int heartBeatTimeout) {
        this.heartBeatTimeout = heartBeatTimeout;
    }

    public int getHeartBeatWaitRetry() {
        return heartBeatWaitRetry;
    }

    public void setHeartBeatWaitRetry(int heartBeatWaitRetry) {
        this.heartBeatWaitRetry = heartBeatWaitRetry;
    }


    @Override
    public String toString() {
        return " \nMessageServerProperties{" +
                "\n  port=" + super.getPort() +
                "\n, ip='" + super.getIp() + '\'' +
                "\n, localHost='" + localHost + '\'' +
                "\n, logLevel=" + super.getLogLevel() +
                "\n, sslEnable=" + super.isSslEnable() +
                "\n, sslCertificate='" + super.getSslCertificate() + '\'' +
                "\n, sslPrivateKey='" + super.getSslPrivateKey() + '\'' +
                "\n, applicationName='" + super.getApplicationName() + '\'' +
                "\n, messageListenersScanPackagePaths=" + messageListenersScanPackagePaths +
                "\n, messageProcessorScanPackagePaths=" + messageProcessorScanPackagePaths +
                "\n, messageProtocolProcessorScanPackagePaths=" + messageProtocolProcessorScanPackagePaths +
                "\n, bossThreads=" + bossThreads +
                "\n, bossOptionConnectTimeoutMillis=" + bossOptionConnectTimeoutMillis +
                "\n, bossOptionSoBacklog=" + bossOptionSoBacklog +
                "\n, bossOptionSoReuseaddr=" + bossOptionSoReuseaddr +
                "\n, workThreads=" + workThreads +
                "\n, workerChildOptionSoKeepalive=" + workerChildOptionSoKeepalive +
                "\n, workerChildOptionTcpNoDelay=" + workerChildOptionTcpNoDelay +
                "\n, workerChildOptionSoReuseaddr=" + workerChildOptionSoReuseaddr +
                "\n, workerChildOptionWriteBufferHighWaterMark=" + workerChildOptionWriteBufferHighWaterMark +
                "\n, workerChildOptionWriteBufferLowWaterMark=" + workerChildOptionWriteBufferLowWaterMark +
                "\n, heartBeatEnable=" + heartBeatEnable +
                "\n, heartBeatTimeout=" + heartBeatTimeout +
                "\n, heartBeatWaitRetry=" + heartBeatWaitRetry +
                "\n, websocketPath='" + websocketPath + '\'' +
                "\n, clusterEnable=" + clusterEnable +
                "\n, nodes=" + nodes +
                "\n, clusterMessageRetry=" + clusterMessageRetry +
                "\n, clusterClientHeartbeatInterval=" + clusterClientHeartbeatInterval +
                "\n, clusterClientIdleReadTimeout=" + clusterClientIdleReadTimeout +
                "\n, clusterClientIdleWriteTimeout=" + clusterClientIdleWriteTimeout +
                "\n, clusterClientIdleReadWriteTimeout=" + clusterClientIdleReadWriteTimeout +
                "\n, clusterClientChannelPoolAcquireTimeoutMillis=" + clusterClientChannelPoolAcquireTimeoutMillis +
                "\n, clusterClientChannelPoolMaxConnection=" + clusterClientChannelPoolMaxConnection +
                "\n, clusterClientChannelPoolMaxPendingAcquires=" + clusterClientChannelPoolMaxPendingAcquires +
                "\n, clusterClientChannelPoolCoreConnection=" + clusterClientChannelPoolCoreConnection +
                "\n, clusterClientHeartbeatWaitRetry=" + clusterClientHeartbeatWaitRetry +
                "\n, clusterSplitBrainDetectionEnable=" + clusterSplitBrainDetectionEnable +
                "\n, clusterSplitBrainDetectionDelayTime=" + clusterSplitBrainDetectionDelayTime +
                '}';
    }
}
