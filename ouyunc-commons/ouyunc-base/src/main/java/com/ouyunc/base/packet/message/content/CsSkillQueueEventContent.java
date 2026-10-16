package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 技能队列发生变化。坐席事件 {@code cs.session.queue.changed}，
 * 或访客批量刷新位次 {@code cs.session.queue.position}（不含个人名次）。
 */
public class CsSkillQueueEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 技能队列编码，取值等于咨询单 queueId。 */
    private String skillCode;

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = normalize(skillCode);
    }
}
