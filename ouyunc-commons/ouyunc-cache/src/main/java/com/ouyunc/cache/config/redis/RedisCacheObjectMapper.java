package com.ouyunc.cache.config.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redis 对象缓存使用的 ObjectMapper。
 * <p>
 * 同一套 RedisTemplate 会写入 Packet、UserEntity、FriendEntity 等不同具体类，读回时没有调用方给出的目标类型，
 * 所以必须在 JSON 里保留类型（默认的数组包裹格式 {@code [类名, 值]}，与历史数据一致）。
 * 校验器只放行 {@code com.ouyunc.} 与 {@code java.util.}（List/Map 的运行时类型）。
 * 禁止 {@link com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator}：
 * 那相当于打开任意类的 AutoType，Redis 被写入恶意 JSON 时会按类名实例化 classpath 上的对象。
 */
public final class RedisCacheObjectMapper {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private RedisCacheObjectMapper() {
    }

    public static ObjectMapper create() {
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        objectMapper.registerModule(javaTimeModule);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ouyunc.")
                .allowIfSubType("java.util.")
                .build();
        objectMapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL);
        return objectMapper;
    }
}
