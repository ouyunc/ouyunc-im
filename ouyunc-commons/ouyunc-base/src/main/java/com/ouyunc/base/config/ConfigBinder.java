package com.ouyunc.base.config;

/**
 * 配置文档安装完成后的模块绑定。
 * <p>
 * {@code ConfigBootstrap.load} 是唯一入口：合并结束后通过 {@code ServiceLoader} 调用本接口。
 * Redis、MQ、数据库在这里把各自前缀读进静态持有者，业务代码之后只读取，不再各自加载。
 * 前缀缺失时应记下空值，真正使用时再报错，避免未启用的组件挡住启动。
 * </p>
 */
public interface ConfigBinder {

    /**
     * 从已经安装的 {@link ConfigRegistry} 绑定本模块配置。
     */
    void bind();

    /**
     * 入口还没执行时，拒绝使用尚未绑定的配置。
     */
    static void requireBound(boolean bound) {
        if (!bound) {
            throw new IllegalStateException("配置尚未加载，请先调用 ConfigBootstrap.load");
        }
    }
}
