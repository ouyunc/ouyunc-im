package com.ouyunc.db.mongo;

import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.db.mongo.properties.MongodbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author fzx
 * @version 1.0
 * @description: mongodb 工厂
 */
public enum MongodbFactory {

    MONGODB_TEMPLATE (1, "mongodb v1.0操作模板"){
        // 默认mongo库名,加载配置文件的时候会有一个默认值来进行初始化，如果不传数据库名称，则使用默认的数据库来操作
        private static final String DEFAULT_DATABASE_NAME;
        // mongodb 库名-操作模板 map
        private static final ConcurrentHashMap<String, MongoTemplate> mongoTemplateMap = new ConcurrentHashMap<>();

        /**
         * 使用默认的mongodb 数据库名进行操作
         */
        @Override
        public MongoTemplate instance() {
            return instance(DEFAULT_DATABASE_NAME);
        }

        static {
            // 判断配置文件中的默认数据库名称是否为空，如果不为空则使用配置文件中的默认数据库名称
            MongodbProperties mongodbProperties = YmlUtil.getActiveProfileValue("ouyunc-server.yml", "ouyunc.db.mongo", MongodbProperties.class);
            DEFAULT_DATABASE_NAME = System.getProperty("mongodb.database.name");
        }
        /**
         * 使用指定的数据库名称来进行操作数据
         */
        @Override
        public MongoTemplate instance(String databaseName) {
            // 进行配置mongoTemplate 的初始化

            return null;
        }
    };
    private final int version;
    private final String description;


    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    MongodbFactory(int version, String description) {
        this.version = version;
        this.description = description;
    }

    private static final Logger log = LoggerFactory.getLogger(MongodbFactory.class);

    public abstract MongoTemplate instance();

    public abstract MongoTemplate instance(String databaseName);

}
