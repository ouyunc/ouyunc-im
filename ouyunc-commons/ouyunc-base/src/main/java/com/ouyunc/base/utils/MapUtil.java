package com.ouyunc.base.utils;

import org.apache.commons.lang3.StringUtils;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fzx
 * @Description: 合并map
 **/
public class MapUtil {

    private static final Objenesis objenesis = new ObjenesisStd(true);


    public static boolean isNotEmpty(Map<?, ?> map) {
        return null != map && !map.isEmpty();
    }


    /**
     * 将uri 特殊字符串转成map
     */
    public static Map<String, Object> wrapParams2Map(String queryParamsStr) {
        if (StringUtils.isBlank(queryParamsStr)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        String[] splitParams = queryParamsStr.split("[&]");
        for (String splitParam : splitParams) {
            String[] paramKeyValue = splitParam.split("[=]");
            //解析出键值
            if (paramKeyValue.length > 1) {
                //正确解析
                result.put(paramKeyValue[0], paramKeyValue[1]);
            } else if (paramKeyValue.length == 1 && StringUtils.isNoneBlank(paramKeyValue[0])) {
                //只有参数没有值，不加入
                result.put(paramKeyValue[0], "");
            }
        }
        return result;
    }

    /**
     * @return java.util.Map
     * @Author fangzhenxun
     * 合并多个map
     * @Description 重载函数，默认覆盖key相同的值
     * @Param [maps]
     **/
    @SafeVarargs
    public static <K, V> Map<K, V> mergerMaps(Map<K, V>... maps) {
        return mergerMaps(true, maps);
    }


    /**
     * @return java.util.Map
     * @Author fangzhenxun
     * 合并多个map
     * @Description 根据cover如果key存在来决定是否覆盖key 对应的值，cover=true,覆盖，cover=false bu
     * @Param [cover, maps]
     **/
    @SafeVarargs
    public static <K, V> Map<K, V> mergerMaps(boolean cover, Map<K, V>... maps) {
        if (maps.length == 0) {
            throw new RuntimeException("mergerMaps非法参数！");
        }
        // 获取传入map的类型
        Map<K, V> map = new ConcurrentHashMap<>();
        for (Map<K, V> kvMap : maps) {
            for (Map.Entry<K, V> entry : kvMap.entrySet()) {
                map.merge(entry.getKey(), entry.getValue(), (oldV, newV) -> cover ? newV : oldV);
            }
        }
        return map;
    }
}
