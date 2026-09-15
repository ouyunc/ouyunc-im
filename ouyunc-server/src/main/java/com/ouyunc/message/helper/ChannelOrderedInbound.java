package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.ChannelAttrUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * 客户端入站「pre+process 单任务」阶段标记。
 * <p>
 * 有序队列同一任务内先跑 preProcessStage，再跑 processStage；PRE 阶段抑制向 PacketHandler 的 fire，
 * 避免 A-pre → B-pre → A-process 交错。集群直连 PacketHandler 的路径不设本标记。
 * </p>
 */
public final class ChannelOrderedInbound {

    private ChannelOrderedInbound() {
    }

    /** 开始前置阶段：清空通过标记。 */
    public static void beginPre(Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PHASE,
                MessageConstant.ORDERED_INBOUND_PHASE_PRE);
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED, null);
    }

    /** 前置通过后进入业务阶段，允许 fire 到 PacketPostHandler。 */
    public static void beginProcess(Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PHASE,
                MessageConstant.ORDERED_INBOUND_PHASE_PROCESS);
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED, null);
    }

    /** 任务结束清理，避免污染后续消息或集群路径。 */
    public static void clear(Channel channel) {
        if (channel == null) {
            return;
        }
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PHASE, null);
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED, null);
    }

    /**
     * PRE 阶段的 fireChannelRead：只记「通过」，不真正 fire。
     *
     * @return true 表示已抑制 fire，调用方应直接返回
     */
    public static boolean tryMarkPassedOnPreFire(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return false;
        }
        String phase = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PHASE);
        if (!MessageConstant.ORDERED_INBOUND_PHASE_PRE.equals(phase)) {
            return false;
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED, Boolean.TRUE);
        return true;
    }

    /**
     * 消费 PRE 通过标记（读后清除），供串联 processStage 判断。
     */
    public static boolean consumePrePassed(Channel channel) {
        if (channel == null) {
            return false;
        }
        Boolean passed = ChannelAttrUtil.getChannelAttribute(channel,
                MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED);
        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_ORDERED_INBOUND_PRE_PASSED, null);
        return Boolean.TRUE.equals(passed);
    }
}
