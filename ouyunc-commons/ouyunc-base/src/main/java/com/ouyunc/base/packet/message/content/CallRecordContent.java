package com.ouyunc.base.packet.message.content;

import com.ouyunc.base.constant.enums.CallStatusEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 语音/视频通话记录公共字段。
 * <p>
 * 状态取值见 {@link CallStatusEnum}；通话不产生媒体 url，仅记录结果与时长。
 */
public class CallRecordContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 通话结果，存 {@link CallStatusEnum#getCode()}（如 completed / missed）。
     */
    private String status;

    /** 通话时长（秒）；未接通时为 0 */
    private int duration;

    /** 呼叫方向：outgoing / incoming（可选） */
    private String direction;

    /** RTC 会话 id（可选，接入 WebRTC 后填充） */
    private String sessionId;

    public CallRecordContent() {
    }

    public CallRecordContent(String status, int duration) {
        this.status = status;
        this.duration = duration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /** 解析 {@link #status} 为枚举，未知值归 {@link CallStatusEnum#COMPLETED}。 */
    public CallStatusEnum statusEnum() {
        return CallStatusEnum.fromCode(status);
    }
}
