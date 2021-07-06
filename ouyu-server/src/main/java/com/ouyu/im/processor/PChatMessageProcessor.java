package com.ouyu.im.processor;

import cn.hutool.json.JSONUtil;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.PChatMessage;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 私聊消息处理器
 * @Version V1.0
 **/
public class PChatMessageProcessor  extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(PChatMessageProcessor.class);




    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 如果是消息的投递，则直接放行
        final PChatMessage pChatMessage = (PChatMessage) packet.getMessage();
        // 如果不可达服务列表不为空，或者当前重试次数不等于0
        if (pChatMessage.isDelivery()){
            // 集群内部处理消息，由于在第一次传递的时候已经校验是否登录了这里不再进行二次校验
            ctx.fireChannelRead(packet);
            return;
        }
        UserHelper.doAuthentication(pChatMessage.getFrom(), ctx, packet);
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {

        // 全量消息也涉及到读扩散，写扩散
        // @todo 本次版本只保证单机可靠，如果该服务宕机则该消息可能会丢失，集群可靠，以及集群消息是否实现心跳则在下个版本完成
        // 当该服务宕机/下线时时需要根据一定策略将分布式缓存中的ip对应的等待ack的消息按照策略拉倒某个服务上
        // 经过各种拦截策略的判断终于到达消息的最终逻辑处理了
        // 1, 所有消息入库(cache/db)；这里如何设置以及存储的选型是非常重要的


        // 需要判断目的地是否是本服务器， 如果不是本服务，判断是否是集群
        //ws 的私聊处理，私聊就是一对一，最终都要转成websocket帧写出
        //1, 先判断消息的序列化方式
        PChatMessage pChatMessage = (PChatMessage) packet.getMessage();
        String from = pChatMessage.getFrom();
        // 根据to从分布式缓存中取出targetServerAddress目标地址
        String to = pChatMessage.getTo();
        // 判断当前packet 是否是经过传递的消息
        boolean delivery = pChatMessage.isDelivery();
        // 4，进行消息的传递和发送,@todo 需要优化类型转换
        Object obj = IMContext.LOGIN_USER_INFO_CACHE.get(to);
        LoginUserInfo loginUserInfo = null;
        if (obj != null) {
            loginUserInfo = JSONUtil.toBean(JSONUtil.toJsonStr(obj), LoginUserInfo.class);
        }
        if (!delivery) {
            // 将消息在本服务器上存储
            IMContext.PACKET_CACHE.put(packet.getPacketId(), packet);
            // 获取登录的服务地址，唯一 @todo 对于服务器端来讲客户端只有在线和离线
            if (loginUserInfo == null) {
                // ======================================离线===========================================
                //将数据存到缓存或db中
                //@todo 消息的存储一般涉及到读扩散/写扩散，经过该服务器的消息
                IMContext.OFFLINE_PACKET_CACHE.put(to, packet);
                //存储成功后 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                UserHelper.doAck(from, to, packet);
            }else {
                // ======================================在线===========================================
                // @todo 需要考虑下用户网络中断后重启客户端导致下线，这个时候在队列中又会持续的推送，当客户端上线时，先推送后拉取未接收的数据
                // 2, 判断是否开启消息回执，如果开启则进行回执客户端，告知服务端已经收到客户端发送的消息,如果没开启则跳过第三步；
                String targetServerAddress = loginUserInfo.getLoginServerAddress();

                // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
                // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
                if (!IMContext.SERVER_CONFIG.isClusterEnable() || IMContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                    // 服务在线
                    // ,@todo 注意离线消息让客户端去pull, 点击某个人去拉去最新的消息（上线会推送每个用户的未读消息数）
                    IMContext.ACK_SCHEDULE_CACHE.put(packet.getPacketId(), IMContext.EVENT_EXECUTORS.scheduleWithFixedDelay(() ->{
                        MessageHelper.sendMessage(packet, to);
                    }, 10, 10, TimeUnit.SECONDS));
                    // 1.1 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                    UserHelper.doAck(from, to, packet);

                    // 清空消息中的无关数据
                    pChatMessage.clear();
                    // 直接发送哈哈哈
                    MessageHelper.sendMessage(packet, to.split(ImConstant.COMMA));
                    return;
                }
                // ,@todo 注意离线消息让客户端去pull, 点击某个人去拉去最新的消息（上线会推送每个用户的未读消息数）
                IMContext.ACK_SCHEDULE_CACHE.put(packet.getPacketId(), IMContext.EVENT_EXECUTORS.scheduleWithFixedDelay(() ->{
                    MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
                }, 10, 10, TimeUnit.SECONDS));
                // 1.1 将packet 以及等待ack 放入定时队列,使用二级缓存，L1本地存储定时任务队列，L2 redis hash存储备份存储消息ack(防止本地服务掉线宕机)， 删除的时候L1与L2都需要删除
                UserHelper.doAck(from, to, packet);
                // 通过集群漫游到目标服务器上
                pChatMessage.setTargetServerAddress(targetServerAddress);
                MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
            }
        }else {
            // 当前packet 是经过路由传递过的包，此时已经判断是在线了，这里不再进行精确判断处理
            if (loginUserInfo != null) {
                String targetServerAddress = loginUserInfo.getLoginServerAddress();
                // 判断如果是单机或者是目标服务，则直接写出去，否则封装packet去路由发送包
                // 如果没有开启集群或者是该服务器是消息的目标服务(消息最终目的地)
                if (!IMContext.SERVER_CONFIG.isClusterEnable() || IMContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                    // 清空消息中的无关数据
                    pChatMessage.clear();
                    // 直接发送哈哈哈
                    MessageHelper.sendMessage(packet, to.split(ImConstant.COMMA));
                    return;
                }
                // 通过集群漫游到目标服务器上
                pChatMessage.setTargetServerAddress(targetServerAddress);
                MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);
            }

        }
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
