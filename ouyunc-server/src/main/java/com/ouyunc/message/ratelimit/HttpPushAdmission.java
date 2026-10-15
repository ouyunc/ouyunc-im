package com.ouyunc.message.ratelimit;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/**
 * HTTP 推送的本机 appKey 预算。扇出按接收人数扣减，用来防止一个调用方占满节点在途额度。
 * 这是节点预算，不是全集群配额；推送打到哪台 IM，就消耗哪台的额度。
 */
public final class HttpPushAdmission {

    private static final String UNKNOWN_APP_KEY = "__unknown__";

    private static volatile KeyedTokenAdmission admission;

    private HttpPushAdmission() {
    }

    /**
     * @param permits 接收人数，至少按 1 次计费，避免空目标绕过
     */
    public static boolean allow(String appKey, int permits) {
        if (!RateLimitSettings.httpPushEnabled()) {
            return true;
        }
        int cost = Math.max(1, permits);
        String key = StringUtils.isBlank(appKey) ? UNKNOWN_APP_KEY : appKey.trim();
        return admission().allow(key, cost);
    }

    private static KeyedTokenAdmission admission() {
        KeyedTokenAdmission current = admission;
        if (current != null) {
            return current;
        }
        synchronized (HttpPushAdmission.class) {
            if (admission == null) {
                admission = new KeyedTokenAdmission(
                        10_000L,
                        Duration.ofMinutes(10),
                        RateLimitSettings.httpPushPerAppKeyPerSecond(),
                        RateLimitSettings.httpPushPerAppKeyBurst());
            }
            return admission;
        }
    }
}
