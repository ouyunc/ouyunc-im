package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;

import java.lang.reflect.Parameter;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

/**
 * Query / 路径片段字符串到方法参数类型的转换。
 */
final class HttpParamConverter {

    private HttpParamConverter() {
    }

    static String resolveName(Parameter p, String annotationValue) {
        if (StringUtils.isNotBlank(annotationValue)) {
            return annotationValue.trim();
        }
        if (p.isNamePresent() && StringUtils.isNotBlank(p.getName())) {
            return p.getName();
        }
        throw new IllegalStateException("请为参数添加注解 name/value，或为模块启用 -parameters 编译参数: " + p);
    }

    /**
     * 可选参数缺省时：包装类型为 null，基本类型为 Java 默认值（与 Spring 常见用法一致，可选数值建议用包装类型）。
     */
    static Object missingOptionalValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return primitiveDefault(type);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    static Object convert(String raw, Class<?> type) throws HttpPipelineException {
        if (type == String.class) {
            return raw;
        }
        if (raw == null) {
            if (!type.isPrimitive()) {
                return null;
            }
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "基本类型参数不能为 null: " + type.getSimpleName());
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            if (!type.isPrimitive()) {
                return null;
            }
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "基本类型参数不能为空: " + type.getSimpleName());
        }
        try {
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
            }
            if (type == byte.class || type == Byte.class) {
                return Byte.parseByte(v);
            }
            if (type == short.class || type == Short.class) {
                return Short.parseShort(v);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(v);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(v);
            }
            if (type == float.class || type == Float.class) {
                return Float.parseFloat(v);
            }
            if (type == double.class || type == Double.class) {
                return Double.parseDouble(v);
            }
            if (type == char.class || type == Character.class) {
                if (v.length() != 1) {
                    throw new IllegalArgumentException();
                }
                return v.charAt(0);
            }
            if (type.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<? extends Enum> en = (Class<? extends Enum>) type;
                return Enum.valueOf(en, v);
            }
        } catch (IllegalArgumentException ex) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "参数类型转换失败: " + raw + " -> " + type.getSimpleName());
        }
        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                "不支持的参数类型: " + type.getName());
    }
}
