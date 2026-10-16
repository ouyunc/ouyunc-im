package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 单个访客的排队位次。事件 {@code cs.session.queue.position}。
 */
public class CsQueuePositionEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 排队位次，从 1 开始。 */
    private Long queuePosition;

    public Long getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Long queuePosition) {
        this.queuePosition = queuePosition;
    }
}
