package com.ouyunc.core.properties.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Key {

    /***
     * 属性配置文件中的key值
     */
    String value();

    /***
     * key 对应的默认值
     */
    String defaultValue() default "";
}