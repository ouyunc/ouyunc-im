package com.ouyunc.client.pool;


import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 内置客户端的channel处理器, 在这个处理类中主要做channel 通道的动态监听
 * 注册和激活：当客户端连接时，首先会触发注册，进行一些初始化的工作，然后激活连接，就可以收发消息了。
 * 断开和注销：当客户端断开时，反向操作，先断开，再注销。
 * 读取消息：当收到客户端消息时，首先读取，然后触发读取完成。
 * 发生异常：不多解释了。
 * 用户事件：由用户触发的各种非常规事件，根据evt的类型来判断不同的事件类型，从而进行不同的处理。
 * 可写状态变更：收到消息后，要回复消息，会先把回复内容写到缓冲区。而缓冲区大小是有一定限制的，当达到上限以后，可写状态就会变为否，不能再写。等缓冲区的内容被冲刷掉后，缓冲区又有了空间，可写状态又会变为是。
 **/
public class MessageClientHeartBeatHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MessageClientHeartBeatHandler.class);



    /**
     * @param ctx
     * @param event
     * @Author fzx
     * @Description 检测用户时间，用于动态对channel的管理, 在触发响应的idle 规则后，会触发这里的事件方法，并作出判断和处理
     * 当核心channel处于空闲状态时会触发这里的事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        Channel channel = ctx.channel();
        // 当该空闲事件触发时，则说明该通道channel没有任何的消息过来,则需要进行判断进行释放处理
        if (event instanceof IdleStateEvent) {
            // 判断该通道是否是存活
            if (channel.isActive()) {
                // @todo 触发了服务端没有及时响应给客户端，可能服务端挂掉， do thing
                System.out.println("dddd");
            }
        } else {
            super.userEventTriggered(ctx, event);
        }
    }

}
