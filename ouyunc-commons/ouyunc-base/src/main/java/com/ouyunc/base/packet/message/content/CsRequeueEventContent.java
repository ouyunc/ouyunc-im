package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 咨询单重新排队且未关闭。事件 {@code cs.session.requeued}。
 */
public class CsRequeueEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 排队位次，从 1 开始。 */
    private Long queuePosition;
    /** 访客查询本人排队状态的短时票据。 */
    private String queueAccessToken;
    /** 给访客看的系统提示。 */
    private String message;

    public Long getQueuePosition() {
        return queuePosition;
    }

    public void setQueuePosition(Long queuePosition) {
        this.queuePosition = queuePosition;
    }

    public String getQueueAccessToken() {
        return queueAccessToken;
    }

    public void setQueueAccessToken(String queueAccessToken) {
        this.queueAccessToken = normalize(queueAccessToken);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = normalize(message);
    }
}
