package com.ouyunc.core.properties.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface LoadProperties {

    /***
     * 资源文件名。进程启动后 {@code ConfigBootstrap} 会把 classpath、外部文件和配置中心
     * 合成一份文档；这里仍然写 ouyunc-server.yml，读取时命中的是合并结果。
     * 环境优先级：-Douyunc.env &gt; OUYUNC_ENV &gt; ouyunc.profiles.active &gt; dev
     */
    String sources();
}
