package com.ouyu.im.utils;

import com.ouyu.im.context.IMContext;
import io.netty.channel.pool.ChannelPool;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fangzhenxun
 * @Description: 合并map
 * @Version V1.0
 **/
public class MapUtil {

    private static Objenesis objenesis = new ObjenesisStd(true);


    /**
     * @Author fangzhenxun
     * 合并多个map
     * @Description  重载函数，默认覆盖key相同的值
     * @Param [maps]
     * @return java.util.Map
     **/
    public static<K, V> Map<K,V> mergerMaps(Map<K, V>... maps) {
        return mergerMaps(true, maps);
    }

    /**
     * @Author fangzhenxun
     * 合并多个map
     * @Description  根据cover如果key存在来决定是否覆盖key 对应的值，cover=true,覆盖，cover=false bu
     * @Param [cover, maps]
     * @return java.util.Map
     **/
//    public static<K, V> Map<K,V> mergerMaps(boolean cover, Map<K, V>... maps) {
//        // 获取传入map的类型
//        Class clazz = maps[0].getClass();
//        Map<K, V> map = null;
//        map = (Map) objenesis.newInstance(clazz);
//        for (Map<K, V> myMap : maps) {
//            for (Map.Entry<K, V> entry : myMap.entrySet()) {
//                map.merge(entry.getKey(), entry.getValue(), (oldV, newV) -> cover ? newV : oldV);
//            }
//        }
//        return map;
//    }

    /**
     * @Author fangzhenxun
     * 合并多个map
     * @Description  根据cover如果key存在来决定是否覆盖key 对应的值，cover=true,覆盖，cover=false bu
     * @Param [cover, maps]
     * @return java.util.Map
     **/
    public static<K, V> Map<K,V> mergerMaps(boolean cover, Map<K, V>... maps) {
        if (maps.length <=0) {
            throw new RuntimeException("mergerMaps非法参数！");
        }
        // 获取传入map的类型
        Map<K, V> map = new ConcurrentHashMap<>();
        for (int i = 0; i < maps.length; i++) {
            for (Map.Entry<K, V> entry : maps[i].entrySet()) {
                map.merge(entry.getKey(), entry.getValue(), (oldV, newV) -> cover ? newV : oldV);
            }
        }
       return map;
    }
}
