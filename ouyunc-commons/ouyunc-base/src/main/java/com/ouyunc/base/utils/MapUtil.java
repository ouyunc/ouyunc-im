package com.ouyunc.base.utils;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

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
