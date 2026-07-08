package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * HTTP 推送请求中的 {@code extra} 对象：解析 {@code at}/{@code ref}/{@code qos} 并映射到内线 {@link com.ouyunc.base.packet.message.Message}；
 * 可选 {@link #extensions} 整体序列化为 {@code Message.extra}。
 */
public class MessagePushExtra implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> at;

    private List<String> ref;

    private Integer qos;

    /**
     * 其它业务扩展键值；非空时序列化为 JSON 字符串写入 {@code Message.extra}。
     */
    private Map<String, Object> extensions;

    public List<String> getAt() {
        return at;
    }

    public void setAt(List<String> at) {
        this.at = at;
    }

    public List<String> getRef() {
        return ref;
    }

    public void setRef(List<String> ref) {
        this.ref = ref;
    }

    public Integer getQos() {
        return qos;
    }

    public void setQos(Integer qos) {
        this.qos = qos;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
}
