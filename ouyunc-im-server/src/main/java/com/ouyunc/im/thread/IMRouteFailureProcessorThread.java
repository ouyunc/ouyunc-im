package com.ouyunc.im.thread;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.MissingPacket;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.Target;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息路由失败处理线程
 **/
public class IMRouteFailureProcessorThread implements Runnable {
    private static Logger log = LoggerFactory.getLogger(IMRouteFailureProcessorThread.class);

    /**
     * 消息
     */
    private Packet packet;

    public IMRouteFailureProcessorThread(Packet packet) {
        this.packet = packet;
    }

    /**
     * @return void
     * @Author fangzhenxun
     * @Description 路由异常后的重试
     */
    @Override
    public void run() {
        log.warn("获取不到可用的服务连接！packetId: {},开始进行重试...", packet.getPacketId());
        final Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        int currentRetry = innerExtraData.getCurrentRetry();
        currentRetry++;
        // 解析protocolBuf 寻找最终目标机, 清空消息中的列表，添加重试次数+1
        Target target = innerExtraData.getTarget();
        innerExtraData.setCurrentRetry(currentRetry);
        innerExtraData.setFromServerAddress(null);
        innerExtraData.setRoutingTables(null);
        message.setExtra(JSON.toJSONString(extraMessage));
        // targetSocketAddress 不改变
        if (log.isDebugEnabled()) {
            log.debug("正在进行第 {} 次重试消息 packetId:{} ", currentRetry, packet.getPacketId());
        }
        if (currentRetry < IMServerContext.SERVER_CONFIG.getClusterMessageRetry()) {
            // 重试次数+1，清空消息中的曾经路由过的服务，封装消息，找到目标主机
            // retry 去处理
            MessageHelper.asyncDeliveryMessage(packet, target);
            return;
        }
        // 如果重试之后还是出现服务不通，则进行服务的下线处理(这一步在内置客户端心跳保活时处理，这里不做服务下线的处理)，也就是将目标主机从本服务的注册表中删除（如果存在），其他服务上的注册表不做同步更新
        // 其实这里注册表中的数据移除不移除没什么太大意义
        // 存入丢失消息到缓存中
        if (packet.getMessageType() == MessageTypeEnum.IM_PRIVATE_CHAT.getValue() || packet.getMessageType() == MessageTypeEnum.IM_GROUP_CHAT.getValue()) {
            // 对于多端的情况，如果已经有
            long now = SystemClock.now();
            IMServerContext.MISSING_MESSAGES_CACHE.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + innerExtraData.getAppKey() + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.FAIL + CacheConstant.FROM + message.getFrom() + CacheConstant.COLON + CacheConstant.TO + IdentityUtil.generalComboIdentity(message.getTo(), innerExtraData.getTarget().getDeviceEnum().getName()), new MissingPacket(packet, IMServerContext.SERVER_CONFIG.getLocalServerAddress(), now), now);
        }
        log.error("已经重试 {} 次,也没解决问题,该消息packetId : {}将被丢弃！", IMServerContext.SERVER_CONFIG.getClusterMessageRetry(), packet.getPacketId());
    }
}
