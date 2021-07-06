package com.ouyu.im.context;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyu.cache.l1.distributed.redis.RedisL1Cache;
import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.processor.MessageProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.ScheduledFuture;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description: IM上下文, @todo 这里面的使用的ConcurrentHashMap 都要替换成caffeine本地内存
 * @Version V1.0
 **/
public class IMContext {
    private static Logger log = LoggerFactory.getLogger(IMContext.class);


    /**
     * 本地服务地址 ${host}:${port}
     */
    public static String LOCAL_ADDRESS;


    /**
     * IM 服务配置文件
     */
    public static IMServerConfig SERVER_CONFIG;


    /**
     * IM 全局时间执行器
     */
    public static final EventExecutorGroup EVENT_EXECUTORS= new DefaultEventExecutorGroup(16);


    /**
     * IM 保存全局服务连接
     */
    public static LoadingCache<InetSocketAddress, ChannelPool> CLUSTER_GLOBAL_SERVER_CONNECTS_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * IM 集群服务注册表
     */
    public static LoadingCache<InetSocketAddress, ChannelPool> CLUSTER_SERVER_REGISTRY_TABLE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });




    /**
     * IM 定时没有接受ack 则发送重新消息 key = packetId, value=等待接收端的ack 的回调定时队列
     */
    public static LoadingCache<Long, ScheduledFuture> ACK_SCHEDULE_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });

    /**
     * IM 外部（本地）用户的通道缓存，该缓存中不包含集群中的内置客户端的channel, 这里的key 可以是手机号/身份证/token 等唯一标识用户的字段
     */
    public static LoadingCache<String, ChannelHandlerContext> LOCAL_USER_CHANNEL_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * IM 集群客户端核心线程
     */
    public static LoadingCache<Integer, ConcurrentHashSet> CLUSTER_CORE_CHANNEL_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * 内置客户端发送syn在一定时间内没有接收到对方返回的ack的次数
     */
    public static LoadingCache<InetSocketAddress, AtomicInteger> MISS_ACK_TIMES_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return new AtomicInteger(0);
        }
        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * 装载消息处理接口的所有实现类
     */
    public static LoadingCache<Byte, MessageProcessor> MESSAGE_PROCESSOR_CACHE = Caffeine.newBuilder().build(new CacheLoader() {
        private Objenesis objenesis = new ObjenesisStd(true);
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            MessageEnum prototype = MessageEnum.prototype((byte) o);
            if (prototype != null) {
                Class aClass = prototype.getMessageProcessorClass();
                if (aClass != null) {
                    return objenesis.newInstance(aClass);
                }
            }
            log.error("没有找到匹配的消息处理类型");
            return null;
        }

        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });

    //=======================================下面的redis实例可以抽离出，使用不同的redis  key前缀来替代，@todo 后期优化===========================
    /**
     * IM 分布式集群中登录的用户信息， 这个可以使用二级缓存来提高效率？使用二级缓存
     */
    public static RedisL1Cache<String, Object> LOGIN_USER_INFO_CACHE = RedisL1Cache.getInstance();


    /**
     * IM packet 存储
     */
    public static RedisL1Cache<Long, Packet> PACKET_CACHE = RedisL1Cache.getInstance();

    /**
     * IM 离线消息存储,key=客户唯一标识，如身份证号，手机号，邮箱等
     */
    public static RedisL1Cache<String, Packet> OFFLINE_PACKET_CACHE = RedisL1Cache.getInstance();


    /**
     * IM 添加好友状态
     */
    public static RedisL1Cache<Long, Object> FRIEND_STATUS_CACHE = RedisL1Cache.getInstance();

}
