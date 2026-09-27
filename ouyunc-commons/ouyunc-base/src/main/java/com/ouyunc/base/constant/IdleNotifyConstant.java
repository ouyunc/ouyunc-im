package com.ouyunc.base.constant;

/**
 * 业务空闲下行文案：Redis Hash field、档位、本机缓存与 CS actionJson 约定。
 */
public final class IdleNotifyConstant {

    private IdleNotifyConstant() {
    }

    /** Hash field 分段：{@code {scope}:{variant}} */
    public static final String FIELD_SEPARATOR = MessageConstant.COLON;

    /** 所有登录 scope 共用的 field 前缀 */
    public static final String WILDCARD_SCOPE = "*";

    /** 首次空闲档（Redis field 后缀 / variant） */
    public static final int STRIKE_FIRST = NumberConstant.NUMBER_1;

    /** 内置兜底文案覆盖到的最高档（仅第 2 次有默认 pre-close/repeat） */
    public static final int STRIKE_BUILTIN_LAST = NumberConstant.NUMBER_2;

    /** 业务上行后通道上的空闲次数（已清零） */
    public static final int STRIKE_CLEARED = NumberConstant.NUMBER_0;

    /** {@code businessIdleCloseStrike <= 0} 时内部表示不因次数关连 */
    public static final int CLOSE_STRIKE_DISABLED = NumberConstant.NUMBER_NEGATIVE_1;

    /** 首次空闲 Redis variant */
    public static final String VARIANT_FIRST = String.valueOf(STRIKE_FIRST);

    /** 仍将关连或本档关连 */
    public static final String VARIANT_PRE_CLOSE = "pre-close";

    /** 不因本档关连的重复提醒 */
    public static final String VARIANT_REPEAT = "repeat";

    /** 内置兜底 field 前缀，如 {@code 2:pre-close} */
    public static final String BUILTIN_STRIKE_PREFIX = STRIKE_BUILTIN_LAST + FIELD_SEPARATOR;

    /** pre-close 文案里的秒数占位 */
    public static final String PLACEHOLDER_INT = "%d";

    /** 兼容运维写成 %s 的秒数占位 */
    public static final String PLACEHOLDER_STRING = "%s";

    /** CS 平台策略 actionJson 中的文案对象名 */
    public static final String ACTION_JSON_KEY = "imIdleNotify";

    /** 本机 Caffeine 名称 */
    public static final String LOCAL_CACHE_NAME = "idleNotifyTextCache";

    /** 本机 Caffeine 最大租户快照数（按 appKey，不是按用户） */
    public static final int LOCAL_CACHE_MAX_SIZE = 2_000;

    /**
     * 与 {@link CacheConstant#IDLE_NOTIFY_RELOAD_CHANNEL} 相同：{@code ouyunc:im:idle-notify:reload}
     */
    public static final String RELOAD_CHANNEL =
            MessageConstant.OUYUNC + MessageConstant.COLON + "im:idle-notify:reload";

    public static String fieldKey(String scopeToken, String variant) {
        return scopeToken + FIELD_SEPARATOR + variant;
    }

    public static String variantWithSuffix(int strike, String suffix) {
        return strike + FIELD_SEPARATOR + suffix;
    }
}
