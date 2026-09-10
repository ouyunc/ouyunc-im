package com.ouyunc.message.properties;

import com.ouyunc.base.constant.enums.GroupMessagePushModeEnum;
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
    List<String> messageListenersScanPackagePaths;

    /**
     * 消息处理器扫描包路径
     */
    @Key("ouyunc.message.processor.scan-package-paths")
    List<String> messageProcessorScanPackagePaths;

    /**
     * 消息分发协议处理器扫描包路径
     */
    @Key("ouyunc.message.protocol-dispatcher-processor.scan-package-paths")
    List<String> messageProtocolProcessorScanPackagePaths;

    /**
     * 是否开启消息拦截器扫描包路径，true 开启，false-关闭
     */
    @Key(value = "ouyunc.message.interceptor.enable", defaultValue = "true")
    boolean messageInterceptorEnable;

    /**
     * 消息拦截器扫描包路径
     */
    @Key("ouyunc.message.interceptor.scan-package-paths")
    List<String> messageInterceptorScanPackagePaths;

    /**
     * HTTP 控制器/Handler 扫描包路径，未配置时默认扫描 com.ouyunc.message.http
     */
    @Key("ouyunc.message.http.processor-scan-package-paths")
    List<String> httpProcessorScanPackagePaths;

    /**
     * HTTP 请求体（聚合后）最大字节数，与 Netty {@link io.netty.handler.codec.http.HttpObjectAggregator} 上限一致；默认 1MB。
     */
    @Key(value = "ouyunc.message.http.max-content-length", defaultValue = "1048576")
    int httpMaxContentLength;

    /**
     * {@code multipart/form-data} 上传请求体最大字节数（业务层限制，可与 {@link #httpMaxContentLength} 独立配置）。
     * 实际 TCP 聚合上限为 {@code max(httpMaxContentLength, 有效 multipart 上限)}。≤0 时与 {@link #httpMaxContentLength} 相同。默认 10MB。
     */
    @Key(value = "ouyunc.message.http.multipart-max-content-length", defaultValue = "10485760")
    int httpMultipartMaxContentLength;

    /**
     * HTTP 业务线程数：{@code >0} 时在独立线程执行 {@link com.ouyunc.message.http.HttpRequestPipeline#prepare}
     * 与 {@link com.ouyunc.message.http.HttpRequestProcessor#process}，写出仍在对应 Channel 的 EventLoop；
     * {@code 0} 表示与 Netty Worker 同线程同步处理（HTTP 推送开启时禁止，会阻塞 EventLoop）。
     */
    @Key(value = "ouyunc.message.http.business-executor-threads", defaultValue = "16")
    int httpBusinessExecutorThreads;

    /**
     * multipart 上传落盘目录（绝对路径或相对路径，相对则相对进程工作目录）；空则使用 {@code ${user.dir}/http-uploads}。
     */
    @Key("ouyunc.message.http.upload-local-directory")
    String httpUploadLocalDirectory;

    /**
     * 是否开启 HTTP 消息推送接口 {@code POST /api/im/message/push}。
     */
    @Key(value = "ouyunc.message.http-push.enabled", defaultValue = "true")
    boolean httpPushEnabled;

    /**
     * HTTP 推送幂等键 TTL，单位秒。
     */
    @Key(value = "ouyunc.message.http-push.idempotent-ttl-seconds", defaultValue = "86400")
    long httpPushIdempotentTtlSeconds;

    /**
     * 客服 ticket 消息保存后是否投递 CS ticket-activity MQ。
     */
    @Key(value = "ouyunc.message.cs.ticket-activity.enabled", defaultValue = "true")
    boolean csTicketActivityEnabled;

    /**
     * 客服坐席通道关闭后是否投递 CS agent-presence MQ（踢技能池）。
     */
    @Key(value = "ouyunc.message.cs.agent-presence.enabled", defaultValue = "true")
    boolean csAgentPresenceEnabled;

    /**
     * 系统/BOT 代发私聊时是否跳过好友校验。
     */
    @Key(value = "ouyunc.message.http-push.skip-friend-check-for-system", defaultValue = "true")
    boolean httpPushSkipFriendCheckForSystem;

    /**
     * HTTP 推送是否启用 JWT 鉴权（from/fromType 仅从 token 解析，并按 scope 校验推送权限）。
     */
    @Key(value = "ouyunc.message.http-push.jwt.enabled", defaultValue = "true")
    boolean httpPushJwtEnabled;

    /**
     * JWT 签名密钥（HS256），长度建议 ≥ 32 字符。
     */
    @Key("ouyunc.message.http-push.jwt.secret")
    String httpPushJwtSecret;

    /**
     * JWT issuer 校验，空则跳过。
     */
    @Key("ouyunc.message.http-push.jwt.issuer")
    String httpPushJwtIssuer;

    /**
     * JWT 中发送方 identity 的 claim 名。
     */
    @Key(value = "ouyunc.message.http-push.jwt.identity-claim", defaultValue = "sub")
    String httpPushJwtIdentityClaim;

    /**
     * JWT 中 appKey 的 claim 名，须与请求头 X-App-Key 一致。
     */
    @Key(value = "ouyunc.message.http-push.jwt.app-key-claim", defaultValue = "appKey")
    String httpPushJwtAppKeyClaim;

    /**
     * JWT 中 fromType 的 claim 名（必填），取值见 {@link com.ouyunc.base.constant.enums.MessageFromToTypeEnum}；
     * 与用户表 {@code type}、登录 {@code scope} 无关联。
     */
    @Key(value = "ouyunc.message.http-push.jwt.from-type-claim", defaultValue = "fromType")
    String httpPushJwtFromTypeClaim;

    /**
     * JWT 中 scope/permissions 的 claim 名（字符串或数组）。
     */
    @Key(value = "ouyunc.message.http-push.jwt.scope-claim", defaultValue = "scope")
    String httpPushJwtScopeClaim;

    /**
     * 是否在接入 Channel 上安装 {@link com.ouyunc.message.handler.MessageLoggingHandler}。
     * 关闭后不再按「每次 socket 读」打日志；大流量下每次 {@code channelRead} 一条，极易刷屏。
     */
    @Key(value = "ouyunc.message.netty-pipeline-logging-enabled", defaultValue = "true")
    boolean nettyPipelineLoggingEnabled;

    /**
     * boss 线程组个数,默认与netty保持一致
     */
    @Key(value = "ouyunc.message.boss.threads", defaultValue = "1")
    int bossThreads;

    /**
     * 连接超时时间, 连接超时毫秒数，默认值30000毫秒即30秒。
     */
    @Key(value = "ouyunc.message.boss.option.connect-timeout-millis", defaultValue = "30000")
    int bossOptionConnectTimeoutMillis;

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
    @Key(value = "ouyunc.message.work.child-option.write-buffer-low-water-mark", defaultValue = "32768")
    int workerChildOptionWriteBufferLowWaterMark;


    /***
     * 全局是否开启心跳，用来检测连接上的客户端需要发送心跳包（只针对外部客户端），默认开启
     */
    @Key(value = "ouyunc.message.client.heart-beat.enable", defaultValue = "true")
    boolean clientHeartBeatEnable;

    /***
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    @Key(value = "ouyunc.message.client.heart-beat.timeout", defaultValue = "15")
    int clientHeartBeatTimeout;

    /***
     * 外部客户端，心跳重试等待次数，默认3次，超过3次没有心跳则关闭外部客户端channel  不能为负数
     */
    @Key(value = "ouyunc.message.client.heart-beat.wait-retry", defaultValue = "3")
    int clientHeartBeatWaitRetry;


    /***
     * 服务端是否开启登录认证，默认开启
     */
    @Key(value = "ouyunc.message.server.login.enable", defaultValue = "true")
    boolean serverLoginEnable;

    /***
     * 服务端监听客户端的登录超时时间，单位秒，默认值5
     */
    @Key(value = "ouyunc.message.server.login.timeout", defaultValue = "5")
    int serverLoginTimeout;

    /**
     * 启动后是否默认接受新登录；运行期可通过摘流接口或优雅关闭翻转
     */
    @Key(value = "ouyunc.message.server.accept-new-connections", defaultValue = "true")
    boolean acceptNewConnections;

    /**
     * 停机时通知客户端主动断开后的等待秒数；超时仍在线则强制关闭残留连接；0 表示通知后立即强制关
     */
    @Key(value = "ouyunc.message.server.drain.wait-seconds", defaultValue = "15")
    int drainWaitSeconds;

    /**
     * 优雅关闭时是否踢掉本机全部已登录长连接（关闭钩子会清理本节点 Redis 会话）
     */
    @Key(value = "ouyunc.message.server.shutdown.kick-clients", defaultValue = "true")
    boolean shutdownKickClients;

    /**
     * 是否启用 Linux epoll / macOS kqueue 原生传输；false 则强制 JDK NIO（排障或 Windows 开发可关）。
     */
    @Key(value = "ouyunc.message.server.native-io.enable", defaultValue = "true")
    boolean serverNativeIoEnable;


    /***
     * 服务端群消息推送模式，PUSH-推送模式，PULL-拉取模式 PULL_PUSH-拉取推送模式
     */
    @Key(value = "ouyunc.message.server.group-message.mode", defaultValue = "PULL_PUSH")
    GroupMessagePushModeEnum groupMessagePushMode;


    /***
     * 群成员数量阈值，超过该值则切换为拉取模式，在推拉模式下，该配置生效，大于改值的群消息，则切换为拉取模式，小于等于该值则切换为推送模式
     */
    @Key(value = "ouyunc.message.server.group-message.threshold", defaultValue = "500")
    int groupMessageThreshold;

    /**
     * 单用户可加入/创建的群数量上限；{@code -1} 不限制。
     */
    @Key(value = "ouyunc.message.group.max-per-user", defaultValue = "500")
    int groupMaxPerUser;

    /**
     * 单群成员数量上限；{@code -1} 不限制。
     */
    @Key(value = "ouyunc.message.group.max-members", defaultValue = "2000")
    int groupMaxMembers;

    /**
     * 是否对外提供 MQTT。关闭后 CONNECT 直接拒绝。
     */
    @Key(value = "ouyunc.message.mqtt.enabled", defaultValue = "true")
    boolean mqttEnabled;

    /***
     * 是否开启qos重试发送机制，默认关闭
     */
    @Key(value = "ouyunc.message.qos.retry.enable", defaultValue = "false")
    boolean qosRetryEnable;

    /***
     *定时循环初始延迟时间，模式是服务端模式时生效,单位秒
     */
    @Key(value = "ouyunc.message.qos.retry.initial-delay", defaultValue = "3")
    long qosRetryInitialDelay;

    /***
     *定时循环初始延迟时间，模式是服务端模式时生效,单位秒
     */
    @Key(value = "ouyunc.message.qos.retry.period", defaultValue = "3")
    long qosRetryPeriod;

    /***
     * 最大循环次数，默认3次，每次循环间隔时间是period, -1 代表一致循环
     */
    @Key(value = "ouyunc.message.qos.retry.max-loops", defaultValue = "3")
    int qosRetryMaxLoops;


    /**
     * 服务端连接websocket的path
     */
    @Key(value = "ouyunc.message.websocket.path")
    String websocketPath;


    /**
     * 是否开启集群，默认否
     */
    @Key(value = "ouyunc.message.cluster.enable", defaultValue = "false")
    boolean clusterEnable;

    /**
     * # 集群中的服务ip + port (包括自己本身的ip + port), 例如：有10 台服务做集群，就把十台的服务端的IP以及端口号写上即可
     */
    @Key(value = "ouyunc.message.cluster.nodes")
    private Set<String> nodes = new HashSet<>();

    /**
     * 集群中消息重试次数，消息如果不通，会进行重试三次
     */
    @Key(value = "ouyunc.message.cluster.message-retry", defaultValue = "3")
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
    long clusterClientChannelPoolAcquireTimeoutMillis;

    /**
     * 集群中客户端channel pool 中最大连接数, 默认100，根据实际并发进行调整
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-max-connection", defaultValue = "100")
    int clusterClientChannelPoolMaxConnection;

    /**
     * 集群中客户端channel pool 中，在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
     * 默认推荐 最大连接数的30%
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-max-pending-acquires", defaultValue = "30")
    int clusterClientChannelPoolMaxPendingAcquires;

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    @Key(value = "ouyunc.message.cluster.client.channel-pool-core-connection", defaultValue = "5")
    int clusterClientChannelPoolCoreConnection;


    /**
     * 集群中内部客户端，发送syn最大等待3个心跳时间段，如果没有及时得到响应则进行本地下线
     */
    @Key(value = "ouyunc.message.cluster.client.heart-beat-wait-retry", defaultValue = "3")
    int clusterClientHeartbeatWaitRetry;


    /**
     * 集群中的服务开启脑裂检测，默认开启
     */
    @Key(value = "ouyunc.message.cluster.server.split-brain-detection.enable", defaultValue = "false")
    boolean clusterSplitBrainDetectionEnable;

    /**
     * 单位分钟，默认10 集群中，开始检测脑裂的延迟时间（服务启动后多久开始进行脑裂的检测），之后就每个心跳检测一次
     */
    @Key(value = "ouyunc.message.cluster.server.split-brain-detection.delay-time", defaultValue = "10")
    long clusterSplitBrainDetectionDelayTime;

    /**
     * 本节点所属分区标识；zone-aware 模式下必填
     */
    @Key(value = "ouyunc.message.cluster.zone.id", defaultValue = "")
    String clusterZoneId;

    /**
     * 本节点是否为跨区网关
     */
    @Key(value = "ouyunc.message.cluster.zone.gateway", defaultValue = "false")
    boolean clusterZoneGateway;

    /**
     * 集群路由模式：flat | zone-aware
     */
    @Key(value = "ouyunc.message.cluster.routing.mode", defaultValue = "flat")
    String clusterRoutingMode;

    /**
     * 跨区路由策略：gateway | any
     */
    @Key(value = "ouyunc.message.cluster.routing.cross-zone-via", defaultValue = "gateway")
    String clusterCrossZoneVia;

    /**
     * 成员发现：open 认 Redis 租约；allowlist 仅连接 cluster.nodes ∪ topology
     */
    @Key(value = "ouyunc.message.cluster.membership.mode", defaultValue = "open")
    String clusterMembershipMode;

    /**
     * Redis 租约失败隔离：none | drain-on-redis-loss。不再因 peer 半数自杀。
     */
    @Key(value = "ouyunc.message.cluster.isolation.action", defaultValue = "drain-on-redis-loss")
    String clusterIsolationAction;

    /**
     * 租约心跳连续失败几次后进入隔离摘流
     */
    @Key(value = "ouyunc.message.cluster.isolation.redis-fail-threshold", defaultValue = "3")
    int clusterIsolationRedisFailThreshold;

    public boolean isMessageInterceptorEnable() {
        return messageInterceptorEnable;
    }

    public void setMessageInterceptorEnable(boolean messageInterceptorEnable) {
        this.messageInterceptorEnable = messageInterceptorEnable;
    }

    public List<String> getMessageInterceptorScanPackagePaths() {
        return messageInterceptorScanPackagePaths;
    }

    public void setMessageInterceptorScanPackagePaths(List<String> messageInterceptorScanPackagePaths) {
        this.messageInterceptorScanPackagePaths = messageInterceptorScanPackagePaths;
    }

    public List<String> getHttpProcessorScanPackagePaths() {
        return httpProcessorScanPackagePaths;
    }

    public void setHttpProcessorScanPackagePaths(List<String> httpProcessorScanPackagePaths) {
        this.httpProcessorScanPackagePaths = httpProcessorScanPackagePaths;
    }

    public int getHttpMaxContentLength() {
        return httpMaxContentLength;
    }

    public void setHttpMaxContentLength(int httpMaxContentLength) {
        this.httpMaxContentLength = httpMaxContentLength;
    }

    public int getHttpMultipartMaxContentLength() {
        return httpMultipartMaxContentLength;
    }

    public void setHttpMultipartMaxContentLength(int httpMultipartMaxContentLength) {
        this.httpMultipartMaxContentLength = httpMultipartMaxContentLength;
    }

    public int getHttpBusinessExecutorThreads() {
        return httpBusinessExecutorThreads;
    }

    public void setHttpBusinessExecutorThreads(int httpBusinessExecutorThreads) {
        this.httpBusinessExecutorThreads = httpBusinessExecutorThreads;
    }

    public String getHttpUploadLocalDirectory() {
        return httpUploadLocalDirectory;
    }

    public void setHttpUploadLocalDirectory(String httpUploadLocalDirectory) {
        this.httpUploadLocalDirectory = httpUploadLocalDirectory;
    }

    public boolean isHttpPushEnabled() {
        return httpPushEnabled;
    }

    public void setHttpPushEnabled(boolean httpPushEnabled) {
        this.httpPushEnabled = httpPushEnabled;
    }

    public long getHttpPushIdempotentTtlSeconds() {
        return httpPushIdempotentTtlSeconds;
    }

    public void setHttpPushIdempotentTtlSeconds(long httpPushIdempotentTtlSeconds) {
        this.httpPushIdempotentTtlSeconds = httpPushIdempotentTtlSeconds;
    }

    public boolean isCsTicketActivityEnabled() {
        return csTicketActivityEnabled;
    }

    public void setCsTicketActivityEnabled(boolean csTicketActivityEnabled) {
        this.csTicketActivityEnabled = csTicketActivityEnabled;
    }

    public boolean isCsAgentPresenceEnabled() {
        return csAgentPresenceEnabled;
    }

    public void setCsAgentPresenceEnabled(boolean csAgentPresenceEnabled) {
        this.csAgentPresenceEnabled = csAgentPresenceEnabled;
    }

    public boolean isHttpPushSkipFriendCheckForSystem() {
        return httpPushSkipFriendCheckForSystem;
    }

    public void setHttpPushSkipFriendCheckForSystem(boolean httpPushSkipFriendCheckForSystem) {
        this.httpPushSkipFriendCheckForSystem = httpPushSkipFriendCheckForSystem;
    }

    public boolean isHttpPushJwtEnabled() {
        return httpPushJwtEnabled;
    }

    public void setHttpPushJwtEnabled(boolean httpPushJwtEnabled) {
        this.httpPushJwtEnabled = httpPushJwtEnabled;
    }

    public String getHttpPushJwtSecret() {
        return httpPushJwtSecret;
    }

    public void setHttpPushJwtSecret(String httpPushJwtSecret) {
        this.httpPushJwtSecret = httpPushJwtSecret;
    }

    public String getHttpPushJwtIssuer() {
        return httpPushJwtIssuer;
    }

    public void setHttpPushJwtIssuer(String httpPushJwtIssuer) {
        this.httpPushJwtIssuer = httpPushJwtIssuer;
    }

    public String getHttpPushJwtIdentityClaim() {
        return httpPushJwtIdentityClaim;
    }

    public void setHttpPushJwtIdentityClaim(String httpPushJwtIdentityClaim) {
        this.httpPushJwtIdentityClaim = httpPushJwtIdentityClaim;
    }

    public String getHttpPushJwtAppKeyClaim() {
        return httpPushJwtAppKeyClaim;
    }

    public void setHttpPushJwtAppKeyClaim(String httpPushJwtAppKeyClaim) {
        this.httpPushJwtAppKeyClaim = httpPushJwtAppKeyClaim;
    }

    public String getHttpPushJwtFromTypeClaim() {
        return httpPushJwtFromTypeClaim;
    }

    public void setHttpPushJwtFromTypeClaim(String httpPushJwtFromTypeClaim) {
        this.httpPushJwtFromTypeClaim = httpPushJwtFromTypeClaim;
    }

    public String getHttpPushJwtScopeClaim() {
        return httpPushJwtScopeClaim;
    }

    public void setHttpPushJwtScopeClaim(String httpPushJwtScopeClaim) {
        this.httpPushJwtScopeClaim = httpPushJwtScopeClaim;
    }

    public boolean isNettyPipelineLoggingEnabled() {
        return nettyPipelineLoggingEnabled;
    }

    public void setNettyPipelineLoggingEnabled(boolean nettyPipelineLoggingEnabled) {
        this.nettyPipelineLoggingEnabled = nettyPipelineLoggingEnabled;
    }

    public long getQosRetryInitialDelay() {
        return qosRetryInitialDelay;
    }

    public void setQosRetryInitialDelay(long qosRetryInitialDelay) {
        this.qosRetryInitialDelay = qosRetryInitialDelay;
    }

    public long getQosRetryPeriod() {
        return qosRetryPeriod;
    }

    public void setQosRetryPeriod(long qosRetryPeriod) {
        this.qosRetryPeriod = qosRetryPeriod;
    }

    public int getQosRetryMaxLoops() {
        return qosRetryMaxLoops;
    }

    public void setQosRetryMaxLoops(int qosRetryMaxLoops) {
        this.qosRetryMaxLoops = qosRetryMaxLoops;
    }

    public boolean isQosRetryEnable() {
        return qosRetryEnable;
    }

    public void setQosRetryEnable(boolean qosRetryEnable) {
        this.qosRetryEnable = qosRetryEnable;
    }

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

    public GroupMessagePushModeEnum getGroupMessagePushMode() {
        return groupMessagePushMode;
    }

    public void setGroupMessagePushMode(GroupMessagePushModeEnum groupMessagePushMode) {
        this.groupMessagePushMode = groupMessagePushMode;
    }

    public int getGroupMessageThreshold() {
        return groupMessageThreshold;
    }

    public void setGroupMessageThreshold(int groupMessageThreshold) {
        this.groupMessageThreshold = groupMessageThreshold;
    }

    public int getGroupMaxPerUser() {
        return groupMaxPerUser;
    }

    public void setGroupMaxPerUser(int groupMaxPerUser) {
        this.groupMaxPerUser = groupMaxPerUser;
    }

    public int getGroupMaxMembers() {
        return groupMaxMembers;
    }

    public void setGroupMaxMembers(int groupMaxMembers) {
        this.groupMaxMembers = groupMaxMembers;
    }

    public boolean isMqttEnabled() {
        return mqttEnabled;
    }

    public void setMqttEnabled(boolean mqttEnabled) {
        this.mqttEnabled = mqttEnabled;
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

    public String getClusterZoneId() {
        return clusterZoneId;
    }

    public void setClusterZoneId(String clusterZoneId) {
        this.clusterZoneId = clusterZoneId;
    }

    public boolean isClusterZoneGateway() {
        return clusterZoneGateway;
    }

    public void setClusterZoneGateway(boolean clusterZoneGateway) {
        this.clusterZoneGateway = clusterZoneGateway;
    }

    public String getClusterRoutingMode() {
        return clusterRoutingMode;
    }

    public void setClusterRoutingMode(String clusterRoutingMode) {
        this.clusterRoutingMode = clusterRoutingMode;
    }

    public String getClusterCrossZoneVia() {
        return clusterCrossZoneVia;
    }

    public void setClusterCrossZoneVia(String clusterCrossZoneVia) {
        this.clusterCrossZoneVia = clusterCrossZoneVia;
    }

    public String getClusterMembershipMode() {
        return clusterMembershipMode;
    }

    public void setClusterMembershipMode(String clusterMembershipMode) {
        this.clusterMembershipMode = clusterMembershipMode;
    }

    public String getClusterIsolationAction() {
        return clusterIsolationAction;
    }

    public void setClusterIsolationAction(String clusterIsolationAction) {
        this.clusterIsolationAction = clusterIsolationAction;
    }

    public int getClusterIsolationRedisFailThreshold() {
        return clusterIsolationRedisFailThreshold;
    }

    public void setClusterIsolationRedisFailThreshold(int clusterIsolationRedisFailThreshold) {
        this.clusterIsolationRedisFailThreshold = clusterIsolationRedisFailThreshold;
    }

    public int getClusterClientHeartbeatWaitRetry() {
        return clusterClientHeartbeatWaitRetry;
    }

    public void setClusterClientHeartbeatWaitRetry(int clusterClientHeartbeatWaitRetry) {
        this.clusterClientHeartbeatWaitRetry = clusterClientHeartbeatWaitRetry;
    }

    public boolean isServerLoginEnable() {
        return serverLoginEnable;
    }

    public void setServerLoginEnable(boolean serverLoginEnable) {
        this.serverLoginEnable = serverLoginEnable;
    }

    public int getServerLoginTimeout() {
        return serverLoginTimeout;
    }

    public void setServerLoginTimeout(int serverLoginTimeout) {
        this.serverLoginTimeout = serverLoginTimeout;
    }

    public boolean isAcceptNewConnections() {
        return acceptNewConnections;
    }

    public void setAcceptNewConnections(boolean acceptNewConnections) {
        this.acceptNewConnections = acceptNewConnections;
    }

    public int getDrainWaitSeconds() {
        return drainWaitSeconds;
    }

    public void setDrainWaitSeconds(int drainWaitSeconds) {
        this.drainWaitSeconds = drainWaitSeconds;
    }

    public boolean isShutdownKickClients() {
        return shutdownKickClients;
    }

    public void setShutdownKickClients(boolean shutdownKickClients) {
        this.shutdownKickClients = shutdownKickClients;
    }

    public boolean isServerNativeIoEnable() {
        return serverNativeIoEnable;
    }

    public void setServerNativeIoEnable(boolean serverNativeIoEnable) {
        this.serverNativeIoEnable = serverNativeIoEnable;
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

    public boolean isClientHeartBeatEnable() {
        return clientHeartBeatEnable;
    }

    public void setClientHeartBeatEnable(boolean clientHeartBeatEnable) {
        this.clientHeartBeatEnable = clientHeartBeatEnable;
    }

    public int getClientHeartBeatTimeout() {
        return clientHeartBeatTimeout;
    }

    public void setClientHeartBeatTimeout(int clientHeartBeatTimeout) {
        this.clientHeartBeatTimeout = clientHeartBeatTimeout;
    }

    public int getClientHeartBeatWaitRetry() {
        return clientHeartBeatWaitRetry;
    }

    public void setClientHeartBeatWaitRetry(int clientHeartBeatWaitRetry) {
        this.clientHeartBeatWaitRetry = clientHeartBeatWaitRetry;
    }

    @Override
    public String toString() {
        return "MessageServerProperties{" +
                super.toString() +
                ", messageListenersScanPackagePaths=" + messageListenersScanPackagePaths +
                ", messageProcessorScanPackagePaths=" + messageProcessorScanPackagePaths +
                ", messageProtocolProcessorScanPackagePaths=" + messageProtocolProcessorScanPackagePaths +
                ", messageInterceptorEnable=" + messageInterceptorEnable +
                ", messageInterceptorScanPackagePaths=" + messageInterceptorScanPackagePaths +
                ", httpProcessorScanPackagePaths=" + httpProcessorScanPackagePaths +
                ", httpMaxContentLength=" + httpMaxContentLength +
                ", httpMultipartMaxContentLength=" + httpMultipartMaxContentLength +
                ", httpBusinessExecutorThreads=" + httpBusinessExecutorThreads +
                ", httpUploadLocalDirectory='" + httpUploadLocalDirectory + '\'' +
                ", httpPushEnabled=" + httpPushEnabled +
                ", httpPushIdempotentTtlSeconds=" + httpPushIdempotentTtlSeconds +
                ", httpPushSkipFriendCheckForSystem=" + httpPushSkipFriendCheckForSystem +
                ", httpPushJwtEnabled=" + httpPushJwtEnabled +
                ", httpPushJwtSecret='" + httpPushJwtSecret + '\'' +
                ", httpPushJwtIssuer='" + httpPushJwtIssuer + '\'' +
                ", httpPushJwtIdentityClaim='" + httpPushJwtIdentityClaim + '\'' +
                ", httpPushJwtAppKeyClaim='" + httpPushJwtAppKeyClaim + '\'' +
                ", httpPushJwtFromTypeClaim='" + httpPushJwtFromTypeClaim + '\'' +
                ", httpPushJwtScopeClaim='" + httpPushJwtScopeClaim + '\'' +
                ", nettyPipelineLoggingEnabled=" + nettyPipelineLoggingEnabled +
                ", bossThreads=" + bossThreads +
                ", bossOptionConnectTimeoutMillis=" + bossOptionConnectTimeoutMillis +
                ", bossOptionSoBacklog=" + bossOptionSoBacklog +
                ", bossOptionSoReuseaddr=" + bossOptionSoReuseaddr +
                ", workThreads=" + workThreads +
                ", workerChildOptionSoKeepalive=" + workerChildOptionSoKeepalive +
                ", workerChildOptionTcpNoDelay=" + workerChildOptionTcpNoDelay +
                ", workerChildOptionSoReuseaddr=" + workerChildOptionSoReuseaddr +
                ", workerChildOptionWriteBufferHighWaterMark=" + workerChildOptionWriteBufferHighWaterMark +
                ", workerChildOptionWriteBufferLowWaterMark=" + workerChildOptionWriteBufferLowWaterMark +
                ", clientHeartBeatEnable=" + clientHeartBeatEnable +
                ", clientHeartBeatTimeout=" + clientHeartBeatTimeout +
                ", clientHeartBeatWaitRetry=" + clientHeartBeatWaitRetry +
                ", serverLoginEnable=" + serverLoginEnable +
                ", serverLoginTimeout=" + serverLoginTimeout +
                ", acceptNewConnections=" + acceptNewConnections +
                ", drainWaitSeconds=" + drainWaitSeconds +
                ", shutdownKickClients=" + shutdownKickClients +
                ", groupMessagePushMode=" + groupMessagePushMode +
                ", groupMessageThreshold=" + groupMessageThreshold +
                ", groupMaxPerUser=" + groupMaxPerUser +
                ", groupMaxMembers=" + groupMaxMembers +
                ", mqttEnabled=" + mqttEnabled +
                ", qosRetryEnable=" + qosRetryEnable +
                ", qosRetryInitialDelay=" + qosRetryInitialDelay +
                ", qosRetryPeriod=" + qosRetryPeriod +
                ", qosRetryMaxLoops=" + qosRetryMaxLoops +
                ", websocketPath='" + websocketPath + '\'' +
                ", clusterEnable=" + clusterEnable +
                ", nodes=" + nodes +
                ", clusterMessageRetry=" + clusterMessageRetry +
                ", clusterClientHeartbeatInterval=" + clusterClientHeartbeatInterval +
                ", clusterClientIdleReadTimeout=" + clusterClientIdleReadTimeout +
                ", clusterClientIdleWriteTimeout=" + clusterClientIdleWriteTimeout +
                ", clusterClientIdleReadWriteTimeout=" + clusterClientIdleReadWriteTimeout +
                ", clusterClientChannelPoolAcquireTimeoutMillis=" + clusterClientChannelPoolAcquireTimeoutMillis +
                ", clusterClientChannelPoolMaxConnection=" + clusterClientChannelPoolMaxConnection +
                ", clusterClientChannelPoolMaxPendingAcquires=" + clusterClientChannelPoolMaxPendingAcquires +
                ", clusterClientChannelPoolCoreConnection=" + clusterClientChannelPoolCoreConnection +
                ", clusterClientHeartbeatWaitRetry=" + clusterClientHeartbeatWaitRetry +
                ", clusterSplitBrainDetectionEnable=" + clusterSplitBrainDetectionEnable +
                ", clusterSplitBrainDetectionDelayTime=" + clusterSplitBrainDetectionDelayTime +
                ", clusterZoneId='" + clusterZoneId + '\'' +
                ", clusterZoneGateway=" + clusterZoneGateway +
                ", clusterRoutingMode='" + clusterRoutingMode + '\'' +
                ", clusterCrossZoneVia='" + clusterCrossZoneVia + '\'' +
                ", clusterMembershipMode='" + clusterMembershipMode + '\'' +
                ", clusterIsolationAction='" + clusterIsolationAction + '\'' +
                ", clusterIsolationRedisFailThreshold=" + clusterIsolationRedisFailThreshold +
                '}';
    }

}
