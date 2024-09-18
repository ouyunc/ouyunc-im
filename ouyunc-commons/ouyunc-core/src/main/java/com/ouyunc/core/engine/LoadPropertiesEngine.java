package com.ouyunc.core.engine;

import com.ouyunc.base.utils.ReflectUtil;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.core.properties.annotation.Key;
import com.ouyunc.core.properties.annotation.LoadProperties;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @author fzx
 * @description 加载配置文件引擎
 */
public class LoadPropertiesEngine{

    /**
     *  加载 yml 全部的属性值值,并映射到实体类
     * @return
     */
    public<T> T loadProperties(Class<T> tClass, Map<?, ?>... imports){
        // 做个校验
        for( Map<?, ?> map : imports ){
            for( Object key : map.keySet() ){
                if( key == null || map.get(key) == null){
                    throw new IllegalArgumentException(String.format("An import contains a null value for key: '%s'", key));
                }
            }
        }
        T t = null;
        LoadProperties annotation = tClass.getAnnotation(LoadProperties.class);
        if (annotation == null) {
            return t;
        }
        try {
            t = tClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 获取配置属性值
        String source = annotation.sources();
        if (StringUtils.isNotBlank(source)) {
            Object mapObj = YmlUtil.getValue(null, source);
            if (mapObj instanceof Map ymlMap) {
                mappingValue(t, ymlMap);
            }
        }
        // 处理导入的属性,进行覆盖
        for( Map<?, ?> map : imports ){
            mappingImportValue(t, map);
        }
        return t;
    }

    /***
     * @author fzx
     * @description 映射yml配置属性值到实体类
     */
    private void mappingImportValue(Object t, Map ymlMap) {
        for (Field field : ReflectUtil.getFields(t.getClass())) {
            Key key = field.getAnnotation(Key.class);
            if (key == null) {
                continue;
            }
            String keyValue = key.value();
            if (StringUtils.isNotBlank(keyValue)) {
                // 切分key
                // 依次遍历该key的值
                Object value = ymlMap.get(keyValue);
                // 开始获取具体的值
                if (value != null) {
                    ReflectUtil.setValueByField(field, t, value);
                }
            }
        }
    }
    /***
     * @author fzx
     * @description 映射yml配置属性值到实体类
     */
    private void mappingValue(Object t, Map ymlMap) {
        for (Field field : ReflectUtil.getFields(t.getClass())) {
            Key key = field.getAnnotation(Key.class);
            if (key == null) {
                continue;
            }
            String keyValue = key.value();
            String defaultValue = key.defaultValue();
            if (StringUtils.isNotBlank(keyValue)) {
                // 切分key
                String[] keys = keyValue.split("[.]");
                Map ymlInfo = new HashMap();
                ymlInfo.putAll(ymlMap);
                // 依次遍历该key的值
                for (int i = 0; i < keys.length; i++) {
                    Object value = ymlInfo.get(keys[i]);
                    if (value == null) {
                        break;
                    }
                    if (i < keys.length - 1) {
                        ymlInfo = (Map) value;
                    }else {
                        Object realValue = ymlInfo.get(keys[i]);
                        // 开始获取具体的值
                        if (realValue != null) {
                            ReflectUtil.setValueByField(field, t, realValue);
                        }
                    }
                }

            }else {
                if (StringUtils.isNotBlank(defaultValue)) {
                    ReflectUtil.setValueByField(field, t, defaultValue);
                }
            }

        }
    }
}
