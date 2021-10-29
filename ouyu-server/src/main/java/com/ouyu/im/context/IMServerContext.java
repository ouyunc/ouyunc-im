package com.ouyu.im.context;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyu.cache.l1.distributed.redis.lettuce.RedisFactory;
import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.entity.HistoryPacket;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.processor.AbstractMessageProcessor;
import com.ouyu.im.processor.MessageProcessor;
import com.ouyu.im.utils.ClassUtil;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description: IM上下文, @todo 这里面的使用的ConcurrentHashMap 都要替换成caffeine本地内存
 * @Version V1.0
 **/
public class IMServerContext extends IMContext{
    private static Logger log = LoggerFactory.getLogger(IMServerContext.class);



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
            List<Class> classList = ClassUtil.getAllClassByInterface(MessageProcessor.class, true);
            MessageEnum prototype = MessageEnum.prototype((byte) o);
            if (prototype != null) {
                for (Class tClass : classList) {
                    AbstractMessageProcessor messageProcessor = (AbstractMessageProcessor) objenesis.newInstance(tClass);
                    if (prototype.equals(messageProcessor.messageType())) {
                        return messageProcessor;
                    }
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
    public static RedisTemplate<String, LoginUserInfo> LOGIN_USER_INFO_CACHE = RedisFactory.redisTemplate();


    /**
     * IM packet 存储
     */
    public static RedisTemplate<String, Packet> PACKET_CACHE = RedisFactory.redisTemplate();
    /**
     * IM  历史消息 存储
     */
    public static RedisTemplate<String, HistoryPacket> HISTORY_PACKET_CACHE = RedisFactory.redisTemplate();

    /**
     * IM  广播消息 存储
     */
    public static RedisTemplate<String, Packet> BROADCAST_PACKET_CACHE = RedisFactory.redisTemplate();

    /**
     * IM 离线消息存储,key=客户唯一标识，如身份证号，手机号，邮箱等
     */
    public static RedisTemplate<String, Packet> OFFLINE_PACKET_CACHE = RedisFactory.redisTemplate();


    /**
     * IM 添加好友状态
     */
    public static RedisTemplate<String, Packet> FRIEND_STATUS_CACHE = RedisFactory.redisTemplate();

}
