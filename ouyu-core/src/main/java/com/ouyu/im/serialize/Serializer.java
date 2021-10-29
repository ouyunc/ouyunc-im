package com.ouyu.im.serialize;

import cn.hutool.core.util.ObjectUtil;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtobufIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import org.nustaq.serialization.FSTConfiguration;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fangzhenxun
 * @Description: 协议包序列化，使用枚举来定义实现类
 * @Version V1.0
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
        ObjectMapper  objectMapper = new ObjectMapper();

        @Override
        public  <T> byte[] serialize(T t)  {
            try {
                return objectMapper.writeValueAsBytes(t);
            } catch (JsonProcessingException e) {
                log.error("json 序列化失败! 原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }
        }

        @Override
        public <T> T deserialize(byte[] data, Class<T> cls){
            try {
                return objectMapper.readValue(data, cls);
            } catch (IOException e) {
                log.error("json 反序列化失败 原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    },
    HESSIAN((byte)3, "hessian", "hessian 序列化") {
        @Override
        public  <T> byte[] serialize(T t)  {
            HessianOutput output = null;
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()){
                output = new HessianOutput(os);
                output.writeObject(t);
                return os.toByteArray();
            } catch (Exception e) {
                log.error("hessian 序列化失败！原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        log.error("hessian 出入流关闭失败！");
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public  <T> T deserialize(byte[] data, Class<T> cls) {
            HessianInput input = null;
            try (ByteArrayInputStream is = new ByteArrayInputStream(data)){
                input = new HessianInput(is);
                return (T) input.readObject(cls);
            } catch (Exception e) {
                log.error("hessian 反序列化失败！原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            } finally {
                if (input != null) {
                    input.close();
                }
            }
        }
    },
    HESSIAN2((byte)4, "hessian2", "hessian2 序列化") {
        @Override
        public  <T> byte[] serialize(T t) {
            Hessian2Output output = null;
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()){
                output = new Hessian2Output(os);
                output.writeObject(t);
                output.flush();
                return os.toByteArray();
            } catch (Exception e) {
                log.error("hessian2 序列化失败！原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }finally {
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        log.error("hessian2 出入流关闭失败！");
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public  <T> T deserialize(byte[] data, Class<T> cls) {
            Hessian2Input input = null;
            try (ByteArrayInputStream is = new ByteArrayInputStream(data)){
                input = new Hessian2Input(is);
                return (T) input.readObject(cls);
            } catch (Exception e) {
                log.error("hessian2 反序列化失败！原因：{}", e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        log.error("hessian2 输入流关闭失败！");
                        e.printStackTrace();
                    }
                }
            }
        }
    },
    KRYO((byte)5, "kryo", "kryo 序列化") {

        @Override
        public <T> byte[] serialize(T t) {
            final Kryo kryo = Serializer.kryoPool().borrow();
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream();Output output = new Output(outputStream)) {
                kryo.writeClassAndObject(output, t);
                output.flush();
                return outputStream.toByteArray();
            }catch (Exception e){
                log.error("kryo 序列化错误，原因：{}",e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            }finally {
                // 用完就释放
                Serializer.kryoPool().release(kryo);
            }
        }

        @Override
        public  <T> T deserialize(byte[] data, Class<T> cls) {
            final Kryo kryo = Serializer.kryoPool().borrow();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data); Input input = new Input(inputStream)){
                return (T) kryo.readClassAndObject(input);
            } catch (Exception e) {
                log.error("kryo 反序列化错误，原因：{}",e.getMessage());
                throw new IllegalStateException(e.getMessage(), e);
            } finally {
                Serializer.kryoPool().release(kryo);
            }
        }
    },
    PROTO_STUFF((byte)6, "protoStuff", "protoStuff(基于protoBuf) 序列化"){
        // 轻量级实例化特定类
        private Objenesis objenesis = new ObjenesisStd(true);
        private final ThreadLocal<LinkedBuffer> LOCAL_BUFFER =
                ThreadLocal.withInitial(() -> LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));

        private LinkedBuffer getBuffer() {
            return LOCAL_BUFFER.get().clear();
        }
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
    },
    THRIFT((byte)7, "thrift", "thrift序列化"){
        @Override
        public <T> byte[] serialize(T t) {
            return new byte[0];
        }

        @Override
        public <T> T deserialize(byte[] bytes, Class<T> cls) {
            return null;
        }
    },
    FST((byte)8, "fst", "fst序列化"){
        @Override
        public <T> byte[] serialize(T t) {
            return fstConfiguration.asByteArray(t);
        }

        @Override
        public <T> T deserialize(byte[] bytes, Class<T> cls) {
            return (T) fstConfiguration.asObject(bytes);
        }
        FSTConfiguration fstConfiguration = FSTConfiguration.createJsonConfiguration(true,false);

    };



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


    private static Logger log = LoggerFactory.getLogger(Serializer.class);

    // 由于在创建schema 耗时，这里存储起来
    private static Map<Class, Schema> cachedSchema = new ConcurrentHashMap<>();
    // 获取schema
    private static  Schema getSchema(Class clazz) {
        Schema schema = cachedSchema.get(clazz);
        if (schema == null) {
            schema = RuntimeSchema.getSchema(clazz);
            if (schema != null) {
                cachedSchema.put(clazz, schema);
            }
        }
        return schema;
    }

    /**
     * @Author fangzhenxun
     * @Description 由于kryo线程不安全，所以使用池化
     * @param
     * @return com.esotericsoftware.kryo.pool.KryoPool
     */
    private static KryoPool kryoPool() {
        return new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            //支持对象循环引用（否则会栈溢出）
            kryo.setReferences(true);
            // 不强制要求注册类（注册行为无法保证多个 JVM 内同一个类的注册编号相同；而且业务系统中大量的 Class 也难以一一注册）
            kryo.setRegistrationRequired(false);
            return kryo;
        }).softReferences().build();
    }

    /**
     * @Author fangzhenxun
     * @Description 返回对应的枚举
     * @param value
     * @return com.ouyu.im.serialize.Serializer
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
     * @Author fangzhenxun
     * @Description 序列化
     * @param t
     * @return byte[]
     */
    public abstract<T> byte[] serialize(T t);

    /**
     * @Author fangzhenxun
     * @Description 反序列化
     * @param bytes
     * @return T
     */
    public abstract<T> T deserialize(byte[] bytes, Class<T> cls);
}
