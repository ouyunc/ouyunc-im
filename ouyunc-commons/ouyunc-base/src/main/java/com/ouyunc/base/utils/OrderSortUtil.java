package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
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
            int o1OrderValue = MessageConstant.ONE_HUNDRED;
            int o2OrderValue = MessageConstant.ONE_HUNDRED;
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
