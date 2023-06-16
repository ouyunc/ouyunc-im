package com.ouyunc.im.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

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
}
