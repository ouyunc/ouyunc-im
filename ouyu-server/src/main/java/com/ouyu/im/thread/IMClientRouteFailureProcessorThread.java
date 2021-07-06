package com.ouyu.im.thread;

import com.ouyu.im.context.IMContext;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.innerclient.processor.AbstractIMClientRouteProcessor;
import com.ouyu.im.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public class IMClientRouteFailureProcessorThread extends AbstractIMClientRouteProcessor {
    private static Logger log = LoggerFactory.getLogger(IMClientRouteFailureProcessorThread.class);

    /**
     *  消息
     */
    private Packet packet;

    public IMClientRouteFailureProcessorThread(Packet packet) {
        this.packet = packet;
    }

    /**
     * @Author fangzhenxun
     * @Description 路由异常后的重试以及下线处理
     * @return void
     */
    @Override
    public void process() {
        final Message message = (Message) packet.getMessage();
        int currentRetry = message.getCurrentRetry();
        currentRetry++;
        // 解析protocolBuf 寻找目标机, 清空消息中的列表，添加重试次数+1
        InetSocketAddress targetSocketAddress = SocketAddressUtil.convert2SocketAddress(message.getTargetServerAddress());
        message.setCurrentRetry(currentRetry);
        message.setRoutingTables(null);
        message.setDelivery(false);
        // targetSocketAddress 不改变
        log.warn("当前正在进行第{}次重试 ", currentRetry);
        if (currentRetry < IMContext.SERVER_CONFIG.getClusterServerRetry()) {
            // 重试次数+1，清空消息中的曾经路由过的服务，封装消息，找到目标主机
            // retry 去处理
            MessageHelper.deliveryMessage(targetSocketAddress, packet);
            return;
        }
        // 如果重试之后还是出现服务不同则进行服务的下线处理，也就是将目标主机从本服务的注册表中删除（如果存在），其他服务上的注册表不做同步更新
        // 其实这里注册表中的数据移除不移除没什么太大意义
        log.error("已经重试{}次,也没解决问题，该消息将被抛弃！",IMContext.SERVER_CONFIG.getClusterServerRetry());
    }
}
