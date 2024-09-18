package com.ouyunc.client;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @Author fzx
 * @Description: 默认 客户端实现类
 **/
public class DefaultMessageClient extends AbstractMessageClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultMessageClient.class);

    /***
     * 定时任务事件执行器
     */
    private static final ScheduledExecutorService SCHEDULED_EVENT_EXECUTORS = Executors.newScheduledThreadPool(16, new BasicThreadFactory.Builder().namingPattern("client-heart-hart-pool-%d").build());



    /**
     * @Author fzx
     * @Description 做一些初始化后的处理，客户端心跳
     */
    @Override
    void afterPropertiesSet() {
        // 初始化客户端之后做的事情，对内置客户端的包活处理，及时更新处理本地服务注册表,定时任务处理
//        SCHEDULED_EVENT_EXECUTORS.scheduleWithFixedDelay(new Runnable() {
//            @Override
//            public void run() {
//                String targetServerAddress = "192.168.0.113:8083";
//                Message message = new Message("123456:1", targetServerAddress, WsMessageContentTypeEnum.PING_CONTENT.getType(), Clock.systemUTC().millis());
//                //  ==============针对以上packet 几种序列化对比: string = SYN=========
//                //     packet            message
//                // protoStuff 150b         80b  内部心跳只用protoStuff序列化/反序列化
//                // protoBuf   156b         83b
//                // kryo       140b         112b
//                // json       355b         184b
//                // hessian2   357b         221b
//                // hessian    430b         235b
//                // fst        650b         315b
//                // jdk        500b         346b
//                Packet packet = new Packet(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), MessageContext.<Long>idGenerator().generateId(), DeviceTypeEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), WsMessageTypeEnum.PING_PONG.getType(), message);
//                // 内部客户端连接池异步传递消息syn ,尝试所有的路径去保持连通
//                MessageClientTemplate.asyncSendMessage(packet, Target.newBuilder().targetIdentity(targetServerAddress).build());
//            }
//        }, 10, 3, TimeUnit.SECONDS);
    }


}
