package com.ouyunc.base.serialize;


import com.ouyunc.base.utils.ObjectUtil;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtobufIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fzx
 * @Description: 协议包序列化，使用枚举来定义实现类
 **/
public enum Serializer {

    JDK((byte)1, "jdk", "jdk 序列化") {
        @Override
        public  <T> byte[] serialize(T t)  {
            return ObjectUtil.serialize(t);
        }

        @Override
        public  <T> T deserialize(byte[] data, Class<T> cls) {
            return ObjectUtil.deserialize(data);
        }
    },
    JSON((byte)2, "json", "json 序列化") {
        @Override
        public  <T> byte[] serialize(T t)  {
            return com.alibaba.fastjson2.JSON.toJSONBytes(t);
        }
        @Override
        public <T> T deserialize(byte[] data, Class<T> cls){
            return com.alibaba.fastjson2.JSON.parseObject(data, cls);
        }
    },

    PROTO_STUFF((byte)6, "protoStuff", "protoStuff(基于protoBuf) 序列化"){
        // 轻量级实例化特定类

        private final Objenesis objenesis = new ObjenesisStd(true);
        private final ThreadLocal<LinkedBuffer> LOCAL_BUFFER =
                ThreadLocal.withInitial(() -> LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));

        // 由于在创建schema 耗时，这里存储起来
        private static final Map<Class<?>, Schema<?>> cachedSchema = new ConcurrentHashMap<>();


        // 获取schema
        @SuppressWarnings("unchecked")
        private static<T> Schema<T> getSchema(Class<T> clazz) {
            Schema<T> schema = (Schema<T>) cachedSchema.get(clazz);
            if (schema == null) {
                schema = RuntimeSchema.getSchema(clazz);
                if (schema != null) {
                    cachedSchema.put(clazz, schema);
                }
            }
            return schema;
        }


        private LinkedBuffer getBuffer() {
            return LOCAL_BUFFER.get().clear();
        }
        @SuppressWarnings("unchecked")
        @Override
        public <T> byte[] serialize(T t) {
            Class<T> cls = (Class<T>) t.getClass();
            LinkedBuffer buffer = getBuffer();
            try {
                Schema<T> schema = getSchema(cls);
                return ProtobufIOUtil.toByteArray(t, schema, buffer);
            } catch (Exception e) {
                log.error("protoBuff serialize 失败");
                throw new IllegalStateException(e.getMessage(), e);
            } finally {
                buffer.clear();
                // 手动释放，方式内存泄漏
                LOCAL_BUFFER.remove();
            }

        }

        @Override
        public  <T> T deserialize(byte[] data, Class<T> cls) {
            try {
                T message = objenesis.newInstance(cls);
                Schema<T> schema = getSchema(cls);
                ProtobufIOUtil.mergeFrom(data, message, schema);
                return message;
            } catch (Exception e) {
                log.error("protoBuff deserialize 失败");
                throw new IllegalStateException(e.getMessage(), e);
            }

        }
    }
    ;



    private byte value;
    private String name;
    private String description;

    Serializer(byte value, String name, String description) {
        this.value = value;
        this.name = name;
        this.description = description;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    private static final Logger log = LoggerFactory.getLogger(Serializer.class);

    /**
     * @Author fzx
     * @Description 返回对应的枚举
     */
    public static Serializer prototype(byte value) {
        for (Serializer serializer : Serializer.values()) {
            if (serializer.value == value) {
                return serializer;
            }
        }
        return null;
    }


    /**
     * @Author fzx
     * @Description 序列化
     */
    public abstract<T> byte[] serialize(T t);

    /**
     * @Author fzx
     * @Description 反序列化
     */
    public abstract<T> T deserialize(byte[] bytes, Class<T> cls);
}
