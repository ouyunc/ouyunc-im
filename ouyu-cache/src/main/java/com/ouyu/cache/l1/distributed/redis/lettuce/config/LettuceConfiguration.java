package com.ouyu.cache.l1.distributed.redis.lettuce.config;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

/**
 * @Author fangzhenxun
 * @Description: lettuce 主配置类
 * @Version V1.0
 **/
public class LettuceConfiguration {


    /**
     * @Author fangzhenxun
     * @Description
     * @return io.lettuce.core.resource.ClientResources
     */
    public ClientResources clientResources() {
        return DefaultClientResources.create();
    }

}
