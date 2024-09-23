package com.ouyunc.message.context;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Sets;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.MessageServer;
import com.ouyunc.message.convert.PacketConverter;
import com.ouyunc.message.dispatcher.ProtocolDispatcherProcessor;
import com.ouyunc.message.processor.AbstractBaseProcessor;
import com.ouyunc.message.processor.AbstractMessageProcessor;
import com.ouyunc.message.properties.MessageServerProperties;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.protocol.PacketProtocol;
import com.ouyunc.message.router.BacktrackMessageRouter;
import com.ouyunc.message.router.Router;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author fzx
 * @description 消息服务端上线文
 */
public class MessageServerContext extends MessageContext {
    private static final Logger log = LoggerFactory.getLogger(MessageServerContext.class);

    /**
     * message 服务实例
     */
    public static MessageServer server;


    /**
     * Message 协议分发处理器
     */
    public static List<ProtocolDispatcherProcessor> protocolDispatcherProcessors = new ArrayList<>();



    /**
     * 缓存消息协议
     */
    public static List<PacketProtocol[]> protocolList = new ArrayList<>();


    /**
     * packet 转换器集合
     */
    public static List<PacketConverter<?>> packetConverterList = new ArrayList<>();

    /**
     * packet 路由器
     */
    public static Router<String, Packet,String> messageRouter = new BacktrackMessageRouter();



    /**
     * 外部（本地）用户的通道channel缓存，该缓存中不包含集群中的内置客户端的channel, 这里的key 可以是手机号/身份证/token 等唯一标识用户的字段
     */
    public static Cache<String, ChannelHandlerContext> localClientRegisterTable = new CaffeineLocalCache<>("clientLocalRegisterTable", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取客户端对应的连接通道，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable ChannelHandlerContext load(String messageTypeValue) throws Exception {
            return null;
        }
    }));



    /**
     * 设备类型缓存
     */
    public static Cache<Byte, DeviceType> deviceTypeCache = new CaffeineLocalCache<>("deviceTypeCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取客户端对应的连接通道，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable DeviceType load(Byte messageTypeValue) throws Exception {
            return null;
        }
    }));

    // ================================================================redis=====================================
    /**
     * 客户端登录信息redis缓存,使用0号库
     */
    public static Cache<String, LoginClientInfo> remoteLoginClientInfoCache = new RedisDistributedCache<>(CacheFactory.REDIS.instance());

    /**
     * 分布式锁redisson
     */
    public static RedissonClient redissonClient = CacheFactory.REDISSON.instance();




    /**
     * 设置设备类型列表
     * @param deviceTypeClass 设备类型枚举类
     */
    public static void addDeviceType(Class<? extends DeviceType> deviceTypeClass) {
        if (deviceTypeClass.isEnum()) {
            DeviceType[] deviceTypeEnumConstants = deviceTypeClass.getEnumConstants();
            if (deviceTypeEnumConstants != null) {
                for (DeviceType deviceTypeEnumConstant : deviceTypeEnumConstants) {
                    deviceTypeCache.put(deviceTypeEnumConstant.getDeviceTypeValue(), deviceTypeEnumConstant);
                }
            }
        }
    }

    /**
     * 缓存消息处理接口的所有实现类, Number 类型是 Byte
     */
    public static Cache<Number, AbstractMessageProcessor<? extends Number>> messageProcessorCache = new CaffeineLocalCache<>("messageProcessorCache", Caffeine.newBuilder().build(new CacheLoader<>() {

        /***
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable AbstractMessageProcessor<? extends Number> load(Number messageTypeValue) throws Exception {
            return null;
        }

    }));


    /**
     * 缓存消息内容处理接口的所有实现类 Number 类型是 Integer
     */
    public static Cache<Number, AbstractBaseProcessor<? extends Number>> messageContentProcessorCache = new CaffeineLocalCache<>("messageContentProcessorCache",Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取消息内容处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable AbstractBaseProcessor<? extends Number> load(Number messageTypeValue) throws Exception {
            return null;
        }
    }));

    /**
     *  集群服务注册表，会动态变化，可能这里的数据会比CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE 中的数据多（原因: 有新的之前没有加入过集群的服务加入到集群中，会更新到这里）
     */
    public static Cache<String, ChannelPool> clusterActiveServerRegistryTableCache = new CaffeineLocalCache<>("clusterActiveServerRegistryTableCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        @Nullable
        @Override
        public ChannelPool load(String node) throws Exception {
            return null;
        }
    }));


    /**
     * 保存全局服务连接，一般保持不变为集群中的所有服务
     */
    public static Cache<String, ChannelPool> clusterGlobalServerRegistryTableCache = new CaffeineLocalCache<>("clusterGlobalServerRegistryTableCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        @Nullable
        @Override
        public ChannelPool load(String node) throws Exception {
            return null;
        }
    }));

    /**
     * 集群内部客户端核心channel数
     */
    public static Cache<Integer, Set<Channel>> clusterClientCoreChannelPoolCache = new CaffeineLocalCache<>("clusterClientCoreChannelPoolCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        @Override
        public Set<Channel> load(Integer channelPoolHashCode) throws Exception {
            return Sets.newConcurrentHashSet();
        }
    }));


    /**
     * 内置客户端ip:port在保活处理中，发送syn在一定时间内没有接收到对方返回的ack的次数
     */
    public static Cache<String, AtomicInteger> clusterClientMissAckTimesCache = new CaffeineLocalCache<>("clusterClientMissAckTimesCache", Caffeine.newBuilder().build(new CacheLoader<String, AtomicInteger>() {

        @Override
        public  AtomicInteger load(String s) throws Exception {
            return new AtomicInteger(0);
        }
    }));


    /**
     * 获取服务端配置信息
     */
    public static MessageServerProperties serverProperties() {
        if (messageProperties instanceof MessageServerProperties serverProperties) {
            return serverProperties;
        }
        throw new RuntimeException("获取服务端属性配置信息失败！请先初始化属性配置");
    }

    /**
     * 协议类型列表
     * @param protocolClass 协议类型列表
     */
    public static void addProtocol(Class<? extends PacketProtocol> protocolClass) {
        if (protocolClass.isEnum()) {
            protocolList.add(protocolClass.getEnumConstants());
        }
    }
    /**
     * 协议类型列表
     * @param packetConverter packet 转换器
     */
    public static void addPacketConverterList(PacketConverter<?> packetConverter) {
        packetConverterList.add(packetConverter);
    }

    /**
     * 查找协议
     */
    public static PacketProtocol findProtocol(byte protocolValue, byte protocolVersion) {
        // 先从MessageProtocol 查找
        PacketProtocol protocol = NativePacketProtocol.prototype(protocolValue, protocolVersion);
        if (protocol != null) {
            return protocol;
        }
        // 如果没有找到则从协议列表中查找
        for (PacketProtocol[] protocols : protocolList) {
            for (PacketProtocol extendProtocol : protocols) {
                if (extendProtocol.getProtocol() == protocolValue && extendProtocol.getProtocolVersion() == protocolVersion) {
                    return extendProtocol;
                }
            }
        }
        log.error("未找到协议：protocol={},protocolVersion={}", protocolValue, protocolVersion);
        throw new MessageException("未找到对应的协议！");
    }





}
