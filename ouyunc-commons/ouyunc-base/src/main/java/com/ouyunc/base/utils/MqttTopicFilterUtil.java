package com.ouyunc.base.utils;

/**
 * MQTT 订阅 filter 与发布 topic 是否匹配（支持 {@code +}、{@code #}，与 {@link MqttCodecUtil#isValidPublishTopicName} 约束一致）。
 */
public final class MqttTopicFilterUtil {

    private MqttTopicFilterUtil() {
    }

    /**
     * @param topicFilter 订阅端 filter（可含通配符）
     * @param topicName   发布端 topic（不得含 {@code +}/{@code #}）
     */
    public static boolean matches(String topicFilter, String topicName) {
        if (topicFilter == null || topicName == null || topicFilter.isEmpty() || topicName.isEmpty()) {
            return false;
        }
        if (topicName.indexOf('+') >= 0 || topicName.indexOf('#') >= 0) {
            return false;
        }
        String[] f = topicFilter.split("/", -1);
        String[] t = topicName.split("/", -1);
        return match(f, 0, t, 0);
    }

    private static boolean match(String[] f, int fi, String[] t, int ti) {
        if (fi >= f.length) {
            return ti >= t.length;
        }
        String seg = f[fi];
        if ("#".equals(seg)) {
            return fi == f.length - 1;
        }
        if (ti >= t.length) {
            return false;
        }
        if ("+".equals(seg)) {
            return match(f, fi + 1, t, ti + 1);
        }
        return seg.equals(t[ti]) && match(f, fi + 1, t, ti + 1);
    }
}
