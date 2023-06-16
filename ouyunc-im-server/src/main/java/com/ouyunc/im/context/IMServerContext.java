package com.ouyunc.im.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Sets;
import com.im.cache.ICache;
import com.im.cache.l1.distributed.redis.RedisDistributedL1Cache;
import com.im.cache.l1.local.caffeine.CaffeineLocalL1Cache;
import com.ouyunc.im.IMServer;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.base.MissingPacket;
import com.ouyunc.im.config.IMServerConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description: IM上下文
 * @Version V3.0
 **/
public class IMServerContext extends IMContext{

    /**
     * ttl Thread local
     */
    public static final ThreadLocal<IMServer> TTL_THREAD_LOCAL = new TransmittableThreadLocal<>();

    /**
     * IM 服务配置文件
     */
    public static IMServerConfig SERVER_CONFIG;


    //================================================本地缓存==========================================
    /**
     * IM 外部（本地）用户的通道channel缓存，该缓存中不包含集群中的内置客户端的channel, 这里的key 可以是手机号/身份证/token 等唯一标识用户的字段
     */
    public static ICache<String, ChannelHandlerContext> USER_REGISTER_TABLE = new CaffeineLocalL1Cache<>("USER_REGISTER_TABLE", Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    }));

    /**
     * IM 集群服务注册表，会动态变化，可能这里的数据会比CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE 中的数据多（原因: 有新的之前没有加入过集群的服务加入到集群中，会更新到这里）
     */
    public static CaffeineLocalL1Cache<InetSocketAddress, ChannelPool> CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE = new CaffeineLocalL1Cache<>("CLUSTER_SERVER_REGISTRY_TABLE", Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    }));


    /**
     * IM 保存全局服务连接，一般保持不变为集群中的所有服务
     */
    public static CaffeineLocalL1Cache<InetSocketAddress, ChannelPool> CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE = new CaffeineLocalL1Cache<>("CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE", Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    }));

    /**
     * 内置客户端在保活处理中，发送syn在一定时间内没有接收到对方返回的ack的次数
     */
    public static ICache<InetSocketAddress, AtomicInteger> CLUSTER_INNER_CLIENT_MISS_ACK_TIMES_CACHE = new CaffeineLocalL1Cache<>("CLUSTER_INNER_CLIENT_MISS_ACK_TIMES_CACHE", Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return new AtomicInteger(0);
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    }));


    /**
     * IM 集群内部客户端核心channel数
     */

    public static ICache<Integer, Set<Channel>> CLUSTER_INNER_CLIENT_CORE_CHANNEL_POOL = new CaffeineLocalL1Cache<>("CLUSTER_INNER_CLIENT_CORE_CHANNEL_CACHE", Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return Sets.newConcurrentHashSet();
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    }));


    //=======================================下面的redis实例可以抽离出，使用不同的redis  key前缀来替代，@todo 后期优化===========================

    /**
     * IM 分布式集群中登录的用户信息， 这个可以使用二级缓存来提高效率？使用二级缓存
     */
    public static RedisDistributedL1Cache<String, LoginUserInfo> LOGIN_USER_INFO_CACHE = new RedisDistributedL1Cache<>();


    /**
     * IM 分布式集群中的im App 连接
     */
    public static RedisDistributedL1Cache<String, LoginUserInfo> LOGIN_IM_APP_CONNECTIONS_CACHE = new RedisDistributedL1Cache<>();

    /**
     * IM 服务中丢失的消息 packet
     */
    public static RedisDistributedL1Cache<String, MissingPacket> MISSING_MESSAGES_CACHE = new RedisDistributedL1Cache<>();



}
