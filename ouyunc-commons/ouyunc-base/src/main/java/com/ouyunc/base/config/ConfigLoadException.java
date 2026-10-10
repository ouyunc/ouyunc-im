package com.ouyunc.base.config;

/**
 * 配置文件或配置中心读取失败。
 * <p>
 * 连接失败由引导程序按 fail-fast 决定是退出还是退回本地文件；
 * YAML 本身不合法时直接抛出，避免带着一份残缺配置把进程拉起来。
 * </p>
 */
public class ConfigLoadException extends RuntimeException {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
