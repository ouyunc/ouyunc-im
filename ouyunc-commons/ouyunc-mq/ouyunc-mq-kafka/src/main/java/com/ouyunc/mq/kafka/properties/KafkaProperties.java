package com.ouyunc.mq.kafka.properties;


import com.ouyunc.mq.kafka.enums.KafkaModeEnum;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: kafka 属性配置文件
 * @author fzx
 * @date 2025/1/13 16:36
 * @version 1.0
 */
public class KafkaProperties {

    /**
     * kafka 默认模式 单实例
     */
    private KafkaModeEnum mode;

    /**
     * common的连接
     **/
    private List<String> bootstrapServers = new ArrayList<>();

    public KafkaModeEnum getMode() {
        return mode;
    }

    public void setMode(KafkaModeEnum mode) {
        this.mode = mode;
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    // 这里对数组进行拆分，做特殊处理
    public void setBootstrapServers(List<String> bootstrapServerList) {
        setBootstrapServers(bootstrapServerList, bootstrapServers);
    }


    static void setBootstrapServers(List<String> bootstrapServerList, List<String> bootstrapServers) {
        if (CollectionUtils.isNotEmpty(bootstrapServerList)) {
            for (String bootstrapServer : bootstrapServerList) {
                String[] bootstrapServerArr = bootstrapServer.split(",");
                for (String bootstrapServerItem : bootstrapServerArr) {
                    bootstrapServers.add(bootstrapServerItem.trim());
                }
            }
        }
    }
}
