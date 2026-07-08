package com.ouyunc.base.utils;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Order;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

/**
 * @author fzx
 * @description order 排序
 */
public class OrderSortUtil {



    /***
     * @author fzx
     * @description 排序,如果没有添加order 注解，则默认值100
     */
    public static void sort(List<?> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        list.sort((o1, o2) -> {
            Order o1Order = o1.getClass().getAnnotation(Order.class);
            Order o2Order = o2.getClass().getAnnotation(Order.class);
            int o1OrderValue = NumberConstant.NUMBER_100;
            int o2OrderValue = NumberConstant.NUMBER_100;
            if (o1Order != null) {
                o1OrderValue = o1Order.value();
            }
            if (o2Order != null) {
                o2OrderValue = o2Order.value();
            }
            return o1OrderValue - o2OrderValue;
        });

    }
}
