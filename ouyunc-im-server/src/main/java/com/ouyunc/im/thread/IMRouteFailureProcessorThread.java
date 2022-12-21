package com.ouyunc.im.thread;

import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.entity.MissingPacket;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 消息路由失败处理线程
 * @Version V3.0
 **/
public class IMRouteFailureProcessorThread implements Runnable {
    private static Logger log = LoggerFactory.getLogger(IMRouteFailureProcessorThread.class);

    /**
     *  消息
     */
    private Packet packet;

    public IMRouteFailureProcessorThread(Packet packet) {
        this.packet = packet;
    }

    /**
     * @Author fangzhenxun
     * @Description 路由异常后的重试
     * @return void
     */
    @Override
    public void run() {
        final Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
        }
        int currentRetry = extraMessage.getCurrentRetry();
        currentRetry++;
        // 解析protocolBuf 寻找目标机, 清空消息中的列表，添加重试次数+1
        InetSocketAddress targetSocketAddress = SocketAddressUtil.convert2SocketAddress(extraMessage.getTargetServerAddress());
        extraMessage.setCurrentRetry(currentRetry);
        extraMessage.setFromServerAddress(null);
        extraMessage.setRoutingTables(null);
        extraMessage.setDelivery(false);
        message.setExtra(JSONUtil.toJsonStr(extraMessage));
        //@todo 检测是否自动设置到packet
        // targetSocketAddress 不改变
        log.warn("当前正在进行第{}次重试 ", currentRetry);
        if (currentRetry < IMServerContext.SERVER_CONFIG.getClusterMessageRetry()) {
            // 重试次数+1，清空消息中的曾经路由过的服务，封装消息，找到目标主机
            // retry 去处理
            MessageHelper.deliveryMessage(packet, targetSocketAddress);
            return;
        }
        // 如果重试之后还是出现服务不同则进行服务的下线处理(这一步在客户端心跳保活是处理，这里不做服务下线的处理)，也就是将目标主机从本服务的注册表中删除（如果存在），其他服务上的注册表不做同步更新
        // 其实这里注册表中的数据移除不移除没什么太大意义
        // 存入丢失消息到缓存中
        if (packet.getMessageType() == MessageEnum.IM_PRIVATE_CHAT.getValue() || packet.getMessageType() == MessageEnum.IM_GROUP_CHAT.getValue()) {
            // 对于多端的情况，如果已经有
            IMServerContext.MISSING_MESSAGES_CACHE.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.FAIL + CacheConstant.FROM + message.getFrom() + CacheConstant.COLON + CacheConstant.TO + IdentityUtil.generalComboIdentity(message.getTo(), extraMessage.getDeviceEnum().getName()), new MissingPacket(packet, IMServerContext.SERVER_CONFIG.getLocalServerAddress(), SystemClock.now()), packet.getPacketId());
        }
        log.error("已经重试{}次,也没解决问题，该消息将被抛弃！", IMServerContext.SERVER_CONFIG.getClusterMessageRetry());
    }
}
