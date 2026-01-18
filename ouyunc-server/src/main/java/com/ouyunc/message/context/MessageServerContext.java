package com.ouyunc.message.context;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.DisruptorEventProducerEnum;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.disruptor.DisruptorEventProducer;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * 客户端登录保活队列，这个是无界队列，可以根据业务场景和使用方式调整，注意内存和垃圾回收相关
     */
    public static final ConcurrentLinkedQueue<LoginClientInfo> clientKeepAliveQueue = new ConcurrentLinkedQueue<>();


    /**
     * Message 协议分发处理器
     */
    public static List<ProtocolDispatcherProcessor> protocolDispatcherProcessors = new ArrayList<>();

    /**
     * 发送消息的拦截器，主要针对MessageHelp类中发送消息的拦截处理，是个拦截器链
     */
    public static List<AbstractMessageInterceptor> messageInterceptorChain = new ArrayList<>();

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



    // ================================================================redis=====================================
    /**
     * 客户端登录信息redis缓存,使用0号库
     */
    public static final Cache<String, LoginClientInfo> remoteLoginClientInfoCache = new RedisDistributedCache<>(CacheFactory.REDIS.instance(), CacheFactory.STRING_REDIS.instance());


    /**
     * 分布式锁redisson
     */
    public static final RedissonClient redissonClient = CacheFactory.REDISSON.instance();


    /**
     * 响应式分布式锁redisson
     */
    public static RedissonReactiveClient reactiveRedissonClient = CacheFactory.REACTIVE_REDISSON.instance();




    // ================================================================local=====================================

    /**
     * disruptor事件生产者缓存
     */
    public static Cache<DisruptorEventProducerEnum, DisruptorEventProducer<?>> disruptorEventProducerCache = new CaffeineLocalCache<>("disruptorEventProducerCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取 disruptor事件生产者缓存
         */
        @Override
        public @Nullable DisruptorEventProducer<?> load(DisruptorEventProducerEnum disruptorEventProducer) throws Exception {
            return null;
        }
    }));


    /**
     * 外部（本地）用户的通道channel缓存，该缓存中不包含集群中的内置客户端的channel, 这里的key 可以是手机号/身份证/token 等唯一标识用户的字段
     */
    public static Cache<String, ChannelHandlerContext> localLoginClientRegisterTable = new CaffeineLocalCache<>("clientLocalRegisterTable", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取客户端对应的连接通道，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable ChannelHandlerContext load(String messageTypeValue) throws Exception {
            return null;
        }
    }));


    /**
     * 存储客户端的信息，所有客户端设备的信息都共享，生命周期与最后长的设备连接一致，设置为过期时间, 这个过期时间根据实际业务来调整，避免避免长时间不过期，占用过大内存
     */
    public static Cache<String, Serializable> localClientInfoCache = new CaffeineLocalCache<>("localClientInfoCache", Caffeine.newBuilder().expireAfterWrite(NumberConstant.NUMBER_30, TimeUnit.DAYS).build(new CacheLoader<>() {
        /***
         * 获取客户端对应的客户端信息
         */
        @Override
        public @Nullable Serializable load(String appKeyIdentity) throws Exception {
            return null;
        }
    }));



    /**
     * 设备类型缓存，这里可以配置通过redis 缓存获取appKey所支持的设备类型，建议通过mq 或redis 的发布订阅来实现，因为appKey 所支持的设备类型一般不会经常变，在服务启动后获取一次，然后每次改变通过发布订阅来实现就可以了，，如果没有则取默认的
     */
    private static final Cache<Byte, DeviceType> defaultDeviceTypeCache = new CaffeineLocalCache<>("deviceTypeCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取客户端对应的连接通道，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable DeviceType load(Byte messageTypeValue) throws Exception {
            return null;
        }
    }));

    /**
     * 设备类型缓存，这里可以配置通过redis 缓存获取appKey所支持的设备类型，建议通过mq 或redis 的发布订阅来实现，因为appKey 所支持的设备类型一般不会经常变，在服务启动后获取一次，然后每次改变通过发布订阅来实现就可以了，，如果没有则取默认的
     */
    private static final Cache<String, Map<Byte, DeviceType>> appKeyDeviceTypeCache = new CaffeineLocalCache<>("deviceTypeCache", Caffeine.newBuilder().build(new CacheLoader<>() {
        /***
         * 获取客户端对应的连接通道，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable Map<Byte, DeviceType> load(String messageTypeValue) throws Exception {
            return null;
        }
    }));


    /**
     * 缓存消息处理接口的所有实现类, Number 类型是 Byte
     */
    public static Cache<Number, AbstractMessageProcessor<? extends Number>> messageProcessorCache = new CaffeineLocalCache<>("messageProcessorCache", Caffeine.newBuilder().build(new CacheLoader<>() {

        /***
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Override
        public @Nullable AbstractMessageProcessor<? extends Number> load(Number messageTypeValue) throws Exception {
            log.error("未找到合适的消息处理器，来处理：{} 类型的消息！", messageTypeValue);
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
        public @Nullable AbstractBaseProcessor<? extends Number> load(Number messageContentTypeValue) throws Exception {
            return null;
        }
    }));

    /**
     *  集群服务注册表ip:port，会动态变化，可能这里的数据会比CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE 中的数据多（原因: 有新的之前没有加入过集群的服务加入到集群中，会更新到这里）
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
     * lua 脚本sha 的映射缓存
     */
    public static Cache<LuaScriptEnum, String> luaScriptShaCache = new CaffeineLocalCache<>("luaScriptSha", Caffeine.newBuilder().build(new CacheLoader<LuaScriptEnum, String>() {

        @Override
        public  String load(LuaScriptEnum s) throws Exception {
            return null;
        }
    }));





    /**
     * 设置设备类型列表
     * @param deviceTypeClass 设备类型枚举类
     */
    public static void addDeviceType(Class<? extends DeviceType> deviceTypeClass) {
        if (deviceTypeClass.isEnum()) {
            DeviceType[] deviceTypeEnumConstants = deviceTypeClass.getEnumConstants();
            if (deviceTypeEnumConstants != null) {
                for (DeviceType deviceTypeEnumConstant : deviceTypeEnumConstants) {
                    defaultDeviceTypeCache.put(deviceTypeEnumConstant.getDeviceTypeValue(), deviceTypeEnumConstant);
                }
            }
        }
    }

    /**
     * 设置appKey设备类型列表
     * @param deviceTypes
     */
    public static void addAppKeyDeviceType(String appKey,Collection<DeviceType> deviceTypes) {
        if (StringUtils.isBlank(appKey) || CollectionUtils.isEmpty(deviceTypes)) {
            log.error("appKey 设备类型列表为空！");
            return;
        }
        appKeyDeviceTypeCache.put(appKey, deviceTypes.stream().filter(Objects::nonNull).collect(Collectors.toMap(DeviceType::getDeviceTypeValue, Function.identity())));
    }


    /**
     * 获取 设备类型在appKey下所支持的设备类型
     */
    public static DeviceType deviceType(String appKey, byte deviceTypeValue) {
        Map<Byte, DeviceType> appKeyDeviceTypeMap = appKeyDeviceTypeCache.get(appKey);
        if (MapUtils.isNotEmpty(appKeyDeviceTypeMap)) {
            DeviceType deviceType = appKeyDeviceTypeMap.get(deviceTypeValue);
            if (deviceType == null) {
                log.error("appKey暂未支持该设备类型：{} 的登录,请配置后重试！", deviceTypeValue);
                throw new MessageException("appKey暂未支持该设备类型："+ deviceTypeValue +"的登录,请配置后重试！");
            }
            return deviceType;
        }
        // 如果appKey 没有单独配置支持的设备类型，则使用全局配置
        DeviceType deviceType = defaultDeviceTypeCache.get(deviceTypeValue);
        if (deviceType == null) {
            log.error("非法设备类型：{}", deviceTypeValue);
            throw new MessageException("非法设备类型："+ deviceTypeValue);
        }
        return deviceType;
    }

    /**
     * 获取本地客户（连接在该服务器上的）端信息， 这里需要有一个类似布隆过滤器的概念，如果首次本地缓存没中，则去redis中获取，无论是否获取到，都存入本地缓存，如果获取到，则真实值存入，如果获取不到则存入一个空值或者进行标记，并设置过期时间，这样在过期时间内再次获取时，就不用请求redis了，直接走本地缓存。除非手动触发更新本地缓存（通过发布订阅）
        在过期后再次请求本地缓存，如果没有值或者标记则请求redis 然后重复以上步骤
     * @param appKey
     * @param identity
     * @return
     */
    public static ClientInfo localClientInfo(String appKey, String identity) {
        if (StringUtils.isNotBlank(identity)) {
            Serializable cacheData = localClientInfoCache.get(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity));
            if (cacheData instanceof ClientInfo clientInfo) {
                return clientInfo;
            }else if (cacheData instanceof Boolean) {
                return null;
            }else {
                // 未缓存过，则去redis中获取
                Object obj = cache.get(CacheConstant.buildRemoteClientInfoCacheKey(appKey,  identity));
                if (obj instanceof ClientInfo clientInfo) {
                    localClientInfoCache.put(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity), clientInfo);
                    return clientInfo;
                }else {
                    localClientInfoCache.put(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity), Boolean.TRUE);
                }
            }
        }
        return null;
    }


    /**
     * 获取identity在 appKey 下所支持的设备类型列表
     */
    public static Collection<DeviceType> deviceTypeList(String appKey, String identity) {
        if (StringUtils.isNotBlank(identity)) {
            ClientInfo clientInfo = localClientInfo(appKey, identity);
            if (clientInfo != null && CollectionUtils.isNotEmpty(clientInfo.getSupportDeviceTypes())) {
                Collection<DeviceType> deviceTypes = Lists.newArrayList();
                for (Byte supportDeviceType :  clientInfo.getSupportDeviceTypes()) {
                    deviceTypes.add(deviceType(appKey, supportDeviceType));
                }
                return deviceTypes;
            }
        }
        return deviceTypeList(appKey);
    }

    /**
     * 获取appKey 下所支持的设备类型列表
     */
    public static Collection<DeviceType> deviceTypeList(String appKey) {
        Map<Byte, DeviceType> appKeyDeviceTypeMap = appKeyDeviceTypeCache.get(appKey);
        if (MapUtils.isNotEmpty(appKeyDeviceTypeMap)) {
            return appKeyDeviceTypeMap.values();
        }
        return defaultDeviceTypeCache.asMap().values();
    }

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
    public static void addPacketConverter(PacketConverter<?> packetConverter) {
        packetConverterList.add(packetConverter);
    }

    /**
     * 协议类型列表
     * @param packetConverters packet 转换器
     */
    public static void addPacketConverterList(List<PacketConverter<?>> packetConverters) {
        packetConverterList.addAll(packetConverters);
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
