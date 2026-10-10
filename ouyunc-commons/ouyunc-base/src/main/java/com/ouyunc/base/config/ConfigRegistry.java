package com.ouyunc.base.config;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动引导安装好的唯一配置文档。
 * <p>
 * {@code generation} 从 0 开始，每安装一次加一。模块配置由引导入口在安装后统一绑定，运行中不再重读。
 * </p>
 */
public final class ConfigRegistry {

    private static final AtomicInteger GENERATION = new AtomicInteger(0);

    private static volatile Map<String, Object> document;

    private static volatile String activeProfile;

    private ConfigRegistry() {
    }

    public static boolean isInstalled() {
        return document != null;
    }

    public static int generation() {
        return GENERATION.get();
    }

    public static String activeProfile() {
        return activeProfile;
    }

    /**
     * 安装合并结果。重复安装直接失败，避免运行中悄悄换掉连接参数。
     */
    public static synchronized void install(Map<String, Object> merged, String profile) {
        if (document != null) {
            throw new IllegalStateException("配置已安装，不能重复加载");
        }
        if (merged == null) {
            throw new IllegalArgumentException("合并后的配置不能为空");
        }
        document = ConfigTree.deepCopy(merged);
        activeProfile = StringUtils.defaultIfBlank(profile, ConfigConstant.DEFAULT_PROFILE);
        GENERATION.incrementAndGet();
    }

    /**
     * 仅测试使用，清空已安装文档。
     */
    public static synchronized void reset() {
        document = null;
        activeProfile = null;
        GENERATION.set(0);
    }

    public static Object find(String dottedKey) {
        Map<String, Object> current = document;
        if (current == null) {
            return null;
        }
        return ConfigTree.find(current, dottedKey);
    }
}
