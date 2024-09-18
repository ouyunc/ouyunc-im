package com.ouyunc.core.properties.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface LoadProperties {

    /***
     * 资源文件路径,目前支持单文件,目前仅支持从classpath 读取配置文件，后续可进行扩展：如 ouyunc-im-server.yml,
     */
    String sources();
}
