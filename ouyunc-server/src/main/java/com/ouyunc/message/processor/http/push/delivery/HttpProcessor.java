package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.Processor;
import com.ouyunc.message.http.HttpPipelineException;

/**
 * HTTP 推送按消息类型的投递策略：{@link #preProcess} 校验 + {@link #process} 异步投递。
 * <p>跨线程唯一载体是 {@link Packet}；HttpContext 止于入口，不进入策略。</p>
 */
public interface HttpProcessor extends Processor<Packet> {

    MessageTypeEnum messageType();

    /**
     * 业务前置校验（好友/群成员/客服 prepare 等）；在幂等占位前调用（verify 池），失败不占位。
     * <p>成功仅产生副作用（改写 packet / 写缓存）；失败抛 {@link HttpPipelineException}。</p>
     */
    void preProcess(Packet packet) throws HttpPipelineException;

    /**
     * 异步核心链路（落库/投递等）。调用时 {@link #preProcess} 已通过；fire-and-forget。
     */
    @Override
    void process(Packet packet);
}
