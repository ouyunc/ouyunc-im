package com.ouyunc.base.utils;

import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * @Author fzx
 * @Description 反射工具类
 **/
public class ReflectUtil {

    /**
     * 根据属性名获取属性
     */
    public static Field getField(String fieldName, Class<?> clazz) {
        Class<?> old = clazz;
        Field field = null;
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                field = clazz.getDeclaredField(fieldName);
                if (field != null) {
                    break;
                }
            } catch (Exception e) {
            }
        }
        if (field == null) {
            throw new NullPointerException(old + "没有" + fieldName + "属性");
        }
        return field;
    }

    /**
     * 获取目标类的属性
     */
    public static Field getField(String fieldName, String className) {
        try {
            return getField(fieldName, Class.forName(className));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 获取目标对象的属性
     */
    public static Field getField(String fieldName, Object object) {
        return getField(fieldName, object.getClass());
    }


    /**
     * 获取当前类的属性 包括父类
     */
    public static List<Field> getFields(Class<?> clazz, Class<?> stopClass) {
        try {
            List<Field> fieldList = new ArrayList<>();
            while (clazz != null && clazz != stopClass) {//当父类为null的时候说明到达了最上层的父类(Object类).
                fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));
                clazz = clazz.getSuperclass(); //得到父类,然后赋给自己
            }
            return fieldList;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")  // on JDK 9
    public static void makeAccessible(Field field) {
        if ((!Modifier.isPublic(field.getModifiers()) ||
                !Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
                Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
            field.setAccessible(true);
        }
    }

    /**
     * 给属性设置值,根据各种简单类型进行转换
     *
     * @param field
     * @param target
     * @param value
     */
    public static void setFieldValue(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (Exception e) {
            // 如果设置字段失败，则可能字段类型不匹配，进行类型转换
            try {
                if (value instanceof String) {
                    String value0 = ((String) value).trim();
                    // 获取value 的类型
                    Class<?> fieldType = field.getType();
                    // 根据属性类型进行转换并赋值
                    if (fieldType == int.class || fieldType == Integer.class) {
                        field.setInt(target, Integer.valueOf(value0).intValue());
                    } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                        field.setBoolean(target, Boolean.valueOf(value0).booleanValue());
                    } else if (fieldType == double.class || fieldType == Double.class) {
                        field.setDouble(target, Double.valueOf(value0).doubleValue());
                    } else if (fieldType == byte.class || fieldType == Byte.class) {
                        field.setByte(target, Byte.valueOf(value0).byteValue());
                    } else if (fieldType == char.class || fieldType == Character.class) {
                        field.setChar(target, value0.charAt(0));
                    } else if (fieldType == short.class || fieldType == Short.class) {
                        field.setShort(target, Short.valueOf(value0).shortValue());
                    } else if (fieldType == float.class || fieldType == Float.class) {
                        field.setFloat(target, Float.valueOf(value0).floatValue());
                    } else if (fieldType == long.class || fieldType == Long.class) {
                        field.setLong(target, Long.valueOf(value0).longValue());
                    } else if (BigInteger.class.isAssignableFrom(fieldType)) {
                        field.set(target, new BigInteger(value0));
                    } else if (BigDecimal.class.isAssignableFrom(fieldType)) {
                        field.set(target, new BigDecimal(value0));
                    } else if (List.class.isAssignableFrom(fieldType)) {
                        String[] split = value0.split(",");
                        if (split.length > 0) {
                            List<String> list = new ArrayList<>();
                            for (String s : split) {
                                list.add(s.trim());
                            }
                            field.set(target, list);
                        }
                    } else if (Set.class.isAssignableFrom(fieldType)) {
                        String[] split = value0.split(",");
                        if (split.length > 0) {
                            Set<String> set = new HashSet<>();
                            for (String s : split) {
                                set.add(s.trim());
                            }
                            field.set(target, set);
                        }
                    } else if (Map.class.isAssignableFrom(fieldType)) {

                        //field.set(target, new BigDecimal(value0));
                    } else if (Enum.class.isAssignableFrom(fieldType)) {
                        Class<Enum> enumType = (Class<Enum>) fieldType;
                        field.set(target, Enum.valueOf(enumType, value0));
                    } else if (Object.class.isAssignableFrom(fieldType)) {
                        // 自定义对象
                        //field.set(target, new BigDecimal(value0));
                    } else {
                        // 其他类型
                        throw new RuntimeException("反射字段 "+field.getName()+" 设置值异常");
                    }

                }else {
                    throw new RuntimeException("反射字段 "+field.getName()+" 设置值异常");
                }
            } catch (Exception e0) {
                throw new RuntimeException("反射字段 "+field.getName()+" 设置值异常");
            }
        }
    }

    /**
     * 给定class类查找类中是否存在该属性
     *
     * @param clazz
     * @param name
     * @return
     */
    public static Field findField(Class<?> clazz, String name) {
        return findField(clazz, name, null);
    }

    /**
     * 给定class类查找类中是否存在该属性
     *
     * @param clazz
     * @param name
     * @param type
     * @return
     */
    public static Field findField(Class<?> clazz, String name, Class<?> type) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            List<Field> fields = getFields(searchType);
            for (Field field : fields) {
                if ((name == null || name.equals(field.getName())) &&
                        (type == null || type.equals(field.getType()))) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }


    /**
     * 获取当前类的属性 包括父类
     */
    public static List<Field> getFields(Class<?> clazz) {
        return getFields(clazz, Object.class);
    }

    private static List<Class<?>> getSuperClasses(Class<?> clazz, Class<?> stopClass) {
        List<Class<?>> classes = new ArrayList<>();
        while (clazz != null && clazz != stopClass) {//当父类为null的时候说明到达了最上层的父类(Object类).
            classes.add(clazz);
            clazz = clazz.getSuperclass(); //得到父类,然后赋给自己
        }
        return classes;
    }


    /**
     * 通过属性赋值
     */
    public static void setValueByFieldName(String fieldName, Object object, Object value) {
        Field field = getField(fieldName, object.getClass());
        setValueByField(field, object, value);
    }

    /**
     * 通过属性赋值
     */
    public static void setValueByField(Field field, Object object, Object value) {
        if (!field.isAccessible()) {
            field.setAccessible(true);
            setFieldValue(field, object, value);
            field.setAccessible(false);
        } else {
            setFieldValue(field, object, value);
        }
    }

    /**
     * 获取属性的值
     */
    public static <T> T getValueByField(String fieldName, Object object) {
        Field field = getField(fieldName, object.getClass());
        return getValueByField(field, object);
    }

    /**
     * 获取属性的值
     */
    public static <T> T getValueByField(Field field, Object object) {
        try {
            Object value;
            if (!field.isAccessible()) {
                field.setAccessible(true);
                value = field.get(object);
                field.setAccessible(false);
            } else {
                value = field.get(object);
            }
            return (T) value;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 通过set方法赋值
     */
    public static void setValueBySetMethod(String fieldName, Object object, Object value) {
        if (object == null) {
            throw new RuntimeException("实例对象不能为空");
        }
        if (value == null) {
            return;
        }
        try {
            String setMethodName = "set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            Method setMethod = getMethod(setMethodName, object.getClass(), value.getClass());
            setMethod.invoke(object, value);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 通过set方法赋值
     */
    public static void setValueBySetMethod(Field field, Object object, Object value) {
        if (object == null) {
            throw new RuntimeException("实例对象不能为空");
        }
        if (value == null) {
            return;
        }
        setValueBySetMethod(field.getName(), object, value);
    }

    /**
     * 通过get方法取值
     */
    public static <T> T getValueByGetMethod(String fieldName, Object object) {
        try {
            if (StringUtils.isNotBlank(fieldName)) {
                String getMethodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                Method getMethod = getMethod(getMethodName, object.getClass());
                return (T) getMethod.invoke(object);
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * 通过get方法取值
     */
    public static <T> T getValueByGetMethod(Field field, Object object) {
        return getValueByGetMethod(field.getName(), object);
    }


    /**
     * 获取某个类的某个方法(当前类和父类)
     */
    public static Method getMethod(String methodName, Class<?> clazz) {
        Method method = null;
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                method = clazz.getDeclaredMethod(methodName);
                break;
            } catch (Exception e) {
            }
        }
        if (method == null) {
            throw new NullPointerException("没有" + methodName + "方法");
        }
        return method;
    }

    /**
     * 获取get方法
     *
     * @param fieldName 属性名
     * @return
     */
    public static String getMethodName(String fieldName) {
        String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        return methodName;
    }

    /**
     * 获取某个类的某个方法(当前类和父类) 带一个参数
     */
    public static Method getMethod(String methodName, Class<?> clazz, Class<?> paramType) {
        Method method = null;
        for (; clazz != Object.class; clazz = clazz.getSuperclass()) {
            try {
                method = clazz.getDeclaredMethod(methodName, paramType);
                if (method != null) {
                    return method;
                }
            } catch (Exception e) {
            }
        }
        if (method == null) {
            throw new NullPointerException(clazz + "没有" + methodName + "方法");
        }
        return method;
    }

    /**
     * 获取某个类的某个方法(当前类和父类)
     */
    public static Method getMethod(String methodName, Object obj) {
        return getMethod(methodName, obj.getClass());
    }

    /**
     * 获取某个类的某个方法(当前类和父类) 一个参数
     */
    public static Method getMethod(String methodName, Object obj, Class<?> paramType) {
        return getMethod(methodName, obj.getClass(), paramType);
    }

    /**
     * 获取某个类的某个方法(当前类和父类)
     */
    public static Method getMethod(String methodName, String clazz) {
        try {
            return getMethod(methodName, Class.forName(clazz));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    /**
     * 获取某个类的某个方法(当前类和父类) 一个参数
     */
    public static Method getMethod(String methodName, String clazz, Class<?> paramType) {
        try {
            return getMethod(methodName, Class.forName(clazz), paramType);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    /**
     * 获取方法上的注解
     */
    public static Annotation getMethodAnnotation(Method method, Class targetAnnotationClass) {
        Annotation methodAnnotation = method.getAnnotation(targetAnnotationClass);
        return methodAnnotation;
    }

    /**
     * 获取属性上的注解
     */
    public static Annotation getFieldAnnotation(Field field, Class targetAnnotationClass) {
        Annotation methodAnnotation = field.getAnnotation(targetAnnotationClass);
        return methodAnnotation;
    }

    /**
     * 获取类上的注解
     *
     * @param targetAnnotationClass 目标注解
     * @param targetObjcetClass     目标类
     * @return 目标注解实例
     */
    public static Annotation getClassAnnotation(Class targetAnnotationClass, Class<?> targetObjcetClass) {
        Annotation methodAnnotation = targetObjcetClass.getAnnotation(targetAnnotationClass);
        return methodAnnotation;
    }

    /**
     * 获取类上的注解
     *
     * @return 目标注解实例
     */
    public static Annotation getClassAnnotation(Class targetAnnotationClass, Object obj) {
        return getClassAnnotation(targetAnnotationClass, obj.getClass());
    }

    /**
     * 获取类上的注解
     *
     * @return 目标注解实例
     */
    public static Annotation getClassAnnotation(Class targetAnnotationClass, String clazz) {
        try {
            return getClassAnnotation(targetAnnotationClass, Class.forName(clazz));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    /**
     * 获取注解某个属性的值
     *
     * @param methodName 属性名
     * @param annotation 目标注解
     * @param <T>        返回类型
     * @throws Exception
     */
    public static <T> T getAnnotationValue(String methodName, Annotation annotation) {
        try {
            Method method = annotation.annotationType().getMethod(methodName);
            Object object = method.invoke(annotation);
            return (T) object;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    /**
     * 获取某个类的某个方法上的某个注解的属性
     *
     * @param methodName            注解属性的名字
     * @param targetAnnotationClass 目标注解
     * @param targetObjecMethodName 目标类的方法
     * @param targetObjectClass     目标类
     * @param <T>                   返回值类型
     */
    public static <T> T getMethodAnnotationValue(String methodName, Class targetAnnotationClass, String targetObjecMethodName, Class targetObjectClass) {
        Method method = getMethod(targetObjecMethodName, targetObjectClass);
        Annotation annotation = getMethodAnnotation(method, targetAnnotationClass);
        return getAnnotationValue(methodName, annotation);
    }

    /**
     * @param methodName            注解属性名
     * @param targetAnnotationClass 目标注解
     * @param targetObjecFieldName  目标属性名字
     * @param targetObjectClass     目标类
     * @param <T>                   返回值类型
     */
    public static <T> T getFieldAnnotationValue(String methodName, Class targetAnnotationClass, String targetObjecFieldName, Class targetObjectClass) {
        Field field = getField(targetObjecFieldName, targetObjectClass);
        Annotation annotation = getFieldAnnotation(field, targetAnnotationClass);
        return getAnnotationValue(methodName, annotation);
    }

    /**
     * 判断 clazz是否是target的子类型或者相等
     */
    public static boolean isSubClassOrEquesClass(Class<?> clazz, Class<?> target) {
        if (clazz == target) {
            return true;
        }
        while (clazz != Object.class) {
            if (clazz == target) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }


    /**
     * @return java.util.List<java.lang.String>
     * @Author fangzhenxun
     * @Description 对象属性值转list，顺序（和对象字段顺序相同）
     * @Date 2019/11/11 20:26
     * @Param [obj]
     **/
    public static List<Object> convertObjToList(Object obj) {
        List<Object> list = new ArrayList<>();
        if (obj == null) {
            return null;
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field f = obj.getClass().getDeclaredField(fields[i].getName());
                f.setAccessible(true);
                Object o = f.get(obj);
                list.add(o);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return list;
    }


}
