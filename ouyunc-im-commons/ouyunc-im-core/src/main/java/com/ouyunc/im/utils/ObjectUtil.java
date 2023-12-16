package com.ouyunc.im.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 对象工具类
 */
public class ObjectUtil {
    private static Logger log = LoggerFactory.getLogger(ObjectUtil.class);


    /**
     * 对象序列化为字节数组
     *
     * @param obj 待序列化的对象
     * @param <T> 对象类型
     * @return 序列化后的字节数组
     * @throws IOException            序列化失败时抛出
     * @throws ClassNotFoundException 找不到对象类时抛出
     */
    public static <T> byte[] serialize(T obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
            oos.flush();
            return baos.toByteArray();
        }catch (Exception e) {
            log.error("jdk 序列化失败！");
        }
        return null;
    }

    /**
     * 对象字节数组反序列化为对象
     *
     * @param bytes 字节数组
     * @param <T>   对象类型
     * @return 反序列化后的对象
     * @throws IOException            反序列化失败时抛出
     * @throws ClassNotFoundException 找不到对象类时抛出
     */
    public static <T> T deserialize(byte[] bytes) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        }catch (Exception e) {
            log.error("jdk 反序列化失败！");
        }
        return null;
    }


    /**
     * @Author fangzhenxun
     * @Description 简单获取接口泛型类, 参数指定第一个
     * @param o
     * @return java.lang.Class<?>
     */
    public static Class<?> getInterfaceGenerics(Object o) {
        return getInterfaceGenerics(o,0);
    }

    /**
     * @Author fangzhenxun
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
