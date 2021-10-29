package com.ouyu.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.entity.HistoryPacket;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @Author fangzhenxun
 * @Description: 消息处理类
 * @Version V1.0
 **/
public interface MessageProcessor {
    Logger log = LoggerFactory.getLogger(MessageProcessor.class);



    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做认证授权相关处理，在真正处理消息前处理
     */
    void preProcess(ChannelHandlerContext ctx, Packet packet);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做逻辑处理
     */
    void doProcess(ChannelHandlerContext ctx, Packet packet);



    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做后逻辑处理
     */
    void postProcess(ChannelHandlerContext ctx, Packet packet);





    /**
     * @Author fangzhenxun
     * @Description 默认处理器
     * @param ctx
     * @param packet
     * @param function
     * @return void
     */
    default void process(ChannelHandlerContext ctx, Packet packet, BiConsumer<ChannelHandlerContext, Packet> function) {

        Message message = (Message) packet.getMessage();
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        // 根据to从分布式缓存中取出targetServerAddress目标地址
        String to = message.getTo();
        // 判断当前packet 是否是经过传递的消息
        boolean delivery = message.isDelivery();
        // 4，进行消息的传递和发送,@todo 需要优化类型转换
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.opsForValue().get(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + to);
        // 判断该消息是否经过路由到这台服务器，也就是这台服务是否是消息的首发服务器,如果是需要做一些处理，如果不是有需要做一些处理
        if (delivery) {
            // 当前packet 是经过路由传递过的包，此时已经判断是在线了（在消息经历首次发送的服务器上判断的），这里不再进行精确判断处理（比如刚好消息发送这台服务器上该目标用户下线了）
            if (loginUserInfo != null) {
                String targetServerAddress = loginUserInfo.getLoginServerAddress();
                // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
                // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
                if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                    // 清空消息中的无关数据
                    message.clear();
                    // 直接发送
                    MessageHelper.sendMessage(packet, to.split(ImConstant.COMMA));
                    return;
                }
                // 通过集群漫游到目标服务器上
                message.setToServerAddress(targetServerAddress);
                MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
            }else {
                // 在传递过程中用户下线，则存储离线数据，并清除定时任务
                IMServerContext.OFFLINE_PACKET_CACHE.opsForValue().set(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX +CacheConstant.OFFLINE_CACHE_PREFIX+ CacheConstant.TO_CACHE_PREFIX + to, packet);
                // 直接处理，清掉该消息对应的定时任务
                ScheduledFuture scheduledFuture = IMServerContext.ACK_SCHEDULE_CACHE.get(packet.getPacketId());
                // 取消任务
                scheduledFuture.cancel(true);
                if (scheduledFuture.isCancelled()) {
                    IMServerContext.ACK_SCHEDULE_CACHE.invalidate(scheduledFuture);
                }
                log.warn("目标服务下线了！");
            }
        }else {
            // @todo 这里是前置处理用户自定义的处理
            function.accept(ctx, packet);
            // 消息首次在该服务器上经过，则设置发送者所在的服务器地址
            message.setFromServerAddress(IMServerContext.LOCAL_ADDRESS);
            // 私聊 Timeline 读扩撒
            // 全量消息 读扩撒 im:message:packet:id = ''
            IMServerContext.PACKET_CACHE.opsForValue().set(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.PACKET_CACHE_PREFIX + packet.getPacketId() , packet);
            // im:message:history:from:a:to:b = hash()
            HistoryPacket historyPacket = new HistoryPacket(false, packet);
            IMServerContext.HISTORY_PACKET_CACHE.opsForHash().put(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.HISTORY_CACHE_PREFIX + CacheConstant.SINGLE_CACHE_PREFIX + CacheConstant.FROM_CACHE_PREFIX  + from + CacheConstant.COLON + CacheConstant.TO_CACHE_PREFIX + to , String.valueOf(packet.getPacketId()), historyPacket);
            // 获取登录的服务地址，唯一 @todo 对于服务器端来讲客户端只有在线和离线
            if (loginUserInfo == null) {
                // ======================================离线===========================================
                //将数据存到缓存或db中
                //@todo 消息的存储一般涉及到读扩散/写扩散，经过该服务器的消息
                // 离线消息 读扩撒 im:message:offline:to:a = hash()
                IMServerContext.OFFLINE_PACKET_CACHE.opsForHash().put(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX +CacheConstant.OFFLINE_CACHE_PREFIX+ CacheConstant.TO_CACHE_PREFIX + to,String.valueOf(packet.getPacketId()), packet);
                //存储成功后 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                // @todo 注意这里只是回应浏览器客户端，这里有可能是在集群环境下消息传递中出现用户下线所触发定时任务走到这里，会重复给客户端发送ack,请考虑会有影响吗？
                UserHelper.doAck(from, to, packet);
            }else {
                // ======================================在线===========================================
                // @todo 需要考虑下用户网络中断后重启客户端导致下线，这个时候在队列中又会持续的推送，当客户端上线时，先推送后拉取未接收的数据
                // 2, 判断是否开启消息回执，如果开启则进行回执客户端，告知服务端已经收到客户端发送的消息,如果没开启则跳过第三步；
                String targetServerAddress = loginUserInfo.getLoginServerAddress();
                // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
                // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
                if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                    // 服务在线
                    // ,@todo 注意离线消息让客户端去pull, 点击某个人去拉去最新的消息（上线会推送每个用户的未读消息数）
                    IMServerContext.ACK_SCHEDULE_CACHE.put(packet.getPacketId(), IMServerContext.EVENT_EXECUTORS.scheduleWithFixedDelay(() ->{
                        MessageHelper.sendMessage(packet, to);
                    }, 10, 10, TimeUnit.SECONDS));
                    // 1.1 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                    UserHelper.doAck(from, to, packet);
                    // 清空消息中的无关数据
                    message.clear();
                    // 直接发送
                    MessageHelper.sendMessage(packet, to.split(ImConstant.COMMA));
                    return;
                }
                // ,@todo 注意离线消息让客户端去pull, 点击某个人去拉去最新的消息（上线会推送每个用户的未读消息数）
                IMServerContext.ACK_SCHEDULE_CACHE.put(packet.getPacketId(), IMServerContext.EVENT_EXECUTORS.scheduleWithFixedDelay(() ->{
                    MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
                }, 10, 10, TimeUnit.SECONDS));
                // 1.1 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                UserHelper.doAck(from, to, packet);
                // 通过集群漫游到目标服务器上
                message.setToServerAddress(targetServerAddress);
                MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
            }

        }
    }

    /**
     * @Author fangzhenxun
     * @Description  广播类消息同意模板
     * @param ctx
     * @param packet
     * @return void
     */
    default void broadcast(ChannelHandlerContext ctx, Packet packet, List<String> tos) {
        if (CollectionUtil.isEmpty(tos)) {
            return;
        }
        Message message = (Message) packet.getMessage();
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        // 判断当前packet 是否是经过传递的消息
        boolean delivery = message.isDelivery();
        // 判断该消息是否经过路由到这台服务器，也就是这台服务是否是消息的首发服务器,如果是需要做一些处理，如果不是有需要做一些处理
        if (delivery) {
            // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
            // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
            if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.LOCAL_ADDRESS.equals(message.getToServerAddress())) {
                // 清空消息中的无关数据
                message.clear();
                // 直接发送
                MessageHelper.sendMessage(packet,  message.getTo().split(ImConstant.COMMA));
                return;
            }
            // 通过集群漫游到目标服务器上
            MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(message.getToServerAddress()), packet);
        }else {
            // 存入全量消息
            IMServerContext.PACKET_CACHE.opsForValue().set(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.PACKET_CACHE_PREFIX + packet.getPacketId() , packet);
            // 存入广播消息
            IMServerContext.BROADCAST_PACKET_CACHE.opsForHash().put(CacheConstant.MESSAGE_COMMON_CACHE_PREFIX + CacheConstant.BROADCAST_CACHE_PREFIX + CacheConstant.IDENTITY_CACHE_PREFIX  + from , String.valueOf(packet.getPacketId()), packet);
            // 循环遍历需要广播的用户唯一标识
            for (String to : tos) {
                // 通过唯一标识找到正在上线的to所在的服务器地址，然后进行广播，找不到的存入离线消息
                LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.opsForValue().get(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + to);
                // 离线，让其主动拉取，不进行推送
                if (loginUserInfo != null) {
                    // @todo 需要考虑下用户网络中断后重启客户端导致下线，这个时候在队列中又会持续的推送，当客户端上线时，先推送后拉取未接收的数据
                    // 2, 判断是否开启消息回执，如果开启则进行回执客户端，告知服务端已经收到客户端发送的消息,如果没开启则跳过第三步；
                    String targetServerAddress = loginUserInfo.getLoginServerAddress();
                    // 消息首次在该服务器上经过，则设置发送者所在的服务器地址
                    message.setFromServerAddress(IMServerContext.LOCAL_ADDRESS);
                    // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
                    // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
                    if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                        // 清空消息中的无关数据
                        message.clear();
                        // 直接发送
                        MessageHelper.sendMessage(packet, to.split(ImConstant.COMMA));
                        return;
                    }
                    // 通过集群漫游到目标服务器上
                    message.setToServerAddress(targetServerAddress);
                    MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
                }
            }
        }
    }

}
