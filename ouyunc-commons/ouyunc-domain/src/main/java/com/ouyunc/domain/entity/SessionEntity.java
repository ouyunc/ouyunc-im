package com.ouyunc.domain.entity;


import java.io.Serial;
import java.io.Serializable;

/**
 * 会话
 */
public class SessionEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否置顶: 1-置顶, 0-不置顶
     */
    private Integer isTop;

    /**
     * 是否静音: 1-静音, 0-不不静音
     */
    private Integer isMute;


    public Integer getIsTop() {
        return isTop;
    }

    public void setIsTop(Integer isTop) {
        this.isTop = isTop;
    }

    public Integer getIsMute() {
        return isMute;
    }

    public void setIsMute(Integer isMute) {
        this.isMute = isMute;
    }

    public static final class Fields {
        public static final String isTop = "is_top";
        public static final String isMute = "is_mute";
    }


    public SessionEntity() {
    }

    public SessionEntity(Integer isTop, Integer isMute) {
        this.isTop = isTop;
        this.isMute = isMute;
    }
}
