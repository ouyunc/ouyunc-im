package com.ouyunc.config;

import java.util.List;

/**
 * 配置中心客户端。实现放在独立模块里，通过 {@code ServiceLoader} 装载，
 * 构造方法里不要建连，真正的网络访问放在 {@link #loadDocuments}。
 */
public interface ConfigCenterClient {

    ConfigCenterType type();

    /**
     * 按基础配置、环境配置的顺序返回 YAML 原文。
     * 节点不存在时跳过，不要放空字符串。连接失败抛 {@link com.ouyunc.base.config.ConfigLoadException}。
     */
    List<String> loadDocuments(ConfigLocator locator, String profile);
}
