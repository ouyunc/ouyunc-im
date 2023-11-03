package com.ouyunc.im.handler;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.domain.ImUser;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jodd.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;

/**
 * 一对一的发送信息托管处理器，注意目前只针对一对一发送消息才会判断是否进行托管，其他可以根据业务自行修改
 */
public class TrusteeshipHandler extends SimpleChannelInboundHandler<Packet> {

    private static final ExecutorService EVENT_EXECUTORS = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("chat-bot-pool-%d").get()));


    /**
     * 最后判断接受者是否被机器人托管，如果是则交给服务器按照一定的策略去处理
     * @param ctx
     * @param packet
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (IMProcessContext.CHAT_BOT_PROCESSOR.size() > 0) {
            Message message = (Message) packet.getMessage();
            // 如果该接受者在用户信息中可以找到并且托管给服务端了，则执行下面的托管逻辑
            ImUser user = DbHelper.getUser(message.getTo());
            if (user != null && (user.getTrusteeship() == IMConstant.TRUSTEESHIP || user.getRobot() == IMConstant.ROBOT)) {
                EVENT_EXECUTORS.execute(() -> IMProcessContext.CHAT_BOT_PROCESSOR.get(0).doProcess(ctx, packet));
            }
        }
    }
}
