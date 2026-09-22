package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.Processor;
import com.ouyunc.message.http.HttpPipelineException;
import reactor.core.publisher.Mono;

/**
 * HTTP 推送按消息类型的投递策略：{@link #preProcess} 校验 + {@link #processMono} 主链路。
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
     * 主链路（MQ 已由入口确认时：Redis 热写 + 尽力投递）。
     * <p>{@code true}＝热写成功或幂等命中（可 COMMITTED）；{@code false}＝热写失败（可重试）。
     * 实时扇出失败不得把结果打成 false。</p>
     */
    Mono<Boolean> processMono(Packet packet);

    /**
     * 兼容异步入口：订阅 {@link #processMono} 并按结果提交/标记幂等。
     * 正式 HTTP 路径应走 {@link com.ouyunc.message.processor.http.push.HttpPushProcessorDelegate#runPipeline}。
     */
    @Override
    default void process(Packet packet) {
        HttpPushDeliverySupport.subscribeDelivery(packet, processMono(packet));
    }
}
