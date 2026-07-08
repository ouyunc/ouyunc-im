package com.ouyunc.core.properties.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface LoadProperties {

    /***
     * 资源文件路径（classpath）。会先加载基础文件，再按环境叠加同名前缀的环境文件：
     * 例如 sources=ouyunc-server.yml 时，会尝试加载 ouyunc-server-{env}.yml 覆盖基础配置。
     * 环境变量优先级：-Douyunc.env > OUYUNC_ENV > dev
     */
    String sources();
}
