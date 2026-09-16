package com.ouyunc.base.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 对象工具类
 */
public class ObjectUtil {
    private static final Logger log = LoggerFactory.getLogger(ObjectUtil.class);


    /**
     * @Author fzx
     * @Description 简单获取接口泛型类, 参数指定第一个
     * @param o
     * @return java.lang.Class<?>
     */
    public static Class<?> getInterfaceGenerics(Object o) {
        return getInterfaceGenerics(o,0);
    }

    /**
     * @Author fzx
     * @Description 简单获取接口泛型类
     * @param o
     * @param index 第几个泛型
     * @return java.lang.Class<?>
     */
    public static Class<?> getInterfaceGenerics(Object o, int index) {
        Type[] types = o.getClass().getGenericInterfaces();
        if (types.length <= index) {
            log.error("该类 {} 没有直接继承泛型接口！", o);
            throw new IllegalArgumentException("该类没有直接继承泛型接口！");
        }
        ParameterizedType parameterizedType = (ParameterizedType) types[index];
        Type type = parameterizedType.getActualTypeArguments()[index];
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        log.error("获取该类 {} 直接实现的泛型类的类型失败！", o);
        throw new IllegalArgumentException("获取该类直接实现的泛型类的类型失败！");
    }

}
