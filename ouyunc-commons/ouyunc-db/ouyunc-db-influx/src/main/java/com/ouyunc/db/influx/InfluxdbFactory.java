package com.ouyunc.db.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.db.influx.properties.InfluxdbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * influxdb 工厂, 后面可以配置多个工厂
 */
public enum InfluxdbFactory {

    INFLUXDB_TEMPLATE(2, "influxdb2 的操作模板"){
        /**
         * influxdb 客户端
         */
        private static final InfluxDBClient influxDBClient;

        static {
            // 加载influxdb 属性配置文件
            InfluxdbProperties influxdbProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.INFLUX_CONFIG_PROPERTIES_PREFIX, InfluxdbProperties.class);
            if (influxdbProperties == null) {
                log.error("获取influxdb配置文件失败");
                throw new RuntimeException("获取influxdb配置文件失败");
            }
            influxDBClient = InfluxDBClientFactory.create(influxdbProperties.getUrl(),influxdbProperties.getToken().toCharArray(),influxdbProperties.getOrg());
        }
        @Override
        public InfluxDBClient instance() {
            return influxDBClient;
        }

    }
    ;


    private final int version;
    private final String description;


    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    InfluxdbFactory(int version, String description) {
        this.version = version;
        this.description = description;
    }

    private static final Logger log = LoggerFactory.getLogger(InfluxdbFactory.class);

    public abstract InfluxDBClient instance();

}
