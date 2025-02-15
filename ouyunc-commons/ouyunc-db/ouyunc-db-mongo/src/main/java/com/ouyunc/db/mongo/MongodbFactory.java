package com.ouyunc.db.mongo;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.db.mongo.properties.MongodbProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.Jsr310Converters;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author fzx
 * @version 1.0
 * @description: mongodb 工厂
 */
public enum MongodbFactory {

    MONGODB_TEMPLATE (NumberConstant.NUMBER_1, "mongodb v1.0操作模板"){
        // 默认mongo库名,加载配置文件的时候会有一个默认值来进行初始化，如果不传数据库名称，则使用默认的数据库来操作
        private static String DEFAULT_DATABASE_NAME = "ouyunc";

        // mongodb 属性配置文件
        private static final MongodbProperties mongodbProperties;

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
            mongodbProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.MONGODB_CONFIG_PROPERTIES_PREFIX, MongodbProperties.class);
            if (mongodbProperties != null) {
                if (StringUtils.isNotBlank(mongodbProperties.getDefaultDatabase())) {
                    DEFAULT_DATABASE_NAME = mongodbProperties.getDefaultDatabase();
                }
            }else {
                throw new RuntimeException("获取mongodb配置文件失败");
            }
        }
        /**
         * 使用指定的数据库名称来进行操作数据
         */
        @Override
        public MongoTemplate instance(String databaseName) {
            // 进行配置mongoTemplate 的初始化
            MongoTemplate mongoTemplate = null;
            if (mongoTemplateMap.get(databaseName) == null) {
                synchronized (MongodbFactory.class) {
                    if (mongoTemplateMap.get(databaseName) == null) {
                        mongoTemplateMap.put(databaseName, mongoTemplate = createMongoTemplate(databaseName));
                    }
                }
            }
            if (mongoTemplate == null) {
                log.error("template 配置失败");
                throw new RuntimeException("template 配置失败");
            }
            return mongoTemplate;
        }

        private MongoTemplate createMongoTemplate(String databaseName) {
            // 创建MongoDB客户端配置
            MongoClientSettings settings = createMongoClientSettings();
            MongoClient mongoClient = MongoClients.create(settings);
            // 创建自定义转换器
            MongoDatabaseFactory mongoDatabaseFactory = new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
            MappingMongoConverter converter = createMongoConverter(mongoDatabaseFactory);
            // 创建并配置MongoTemplate
            MongoTemplate template = new MongoTemplate(mongoDatabaseFactory, converter);
            // 设置写入关注
            template.setWriteConcern(WriteConcern.MAJORITY);
            // 设置读取首选项
            template.setReadPreference(ReadPreference.primary());
            // 设置写入结果检查模式
            template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
            return template;
        }

        private static MongoClientSettings createMongoClientSettings() {
            return MongoClientSettings.builder()
                    // ===== 基本连接配置 =====
                    .applyConnectionString(new ConnectionString(mongodbProperties.getUri()))

                    // ===== 连接池配置 =====
                    .applyToConnectionPoolSettings(builder -> {
                        builder.maxSize(mongodbProperties.getPool().getMaxSize())
                                .minSize(mongodbProperties.getPool().getMinSize())
                                .maxWaitTime(mongodbProperties.getPool().getMaxWaitTime(),
                                        TimeUnit.MILLISECONDS)
                                .maxConnectionLifeTime(mongodbProperties.getPool().getMaxLifeTime(),
                                        TimeUnit.MILLISECONDS)
                                .maxConnectionIdleTime(mongodbProperties.getPool().getMaxIdleTime(),
                                        TimeUnit.MILLISECONDS)
                                .maintenanceInitialDelay(mongodbProperties.getPool().getMaintenanceInitialDelay(), TimeUnit.MILLISECONDS)
                                .maintenanceFrequency(mongodbProperties.getPool().getMaintenanceFrequency(), TimeUnit.MINUTES)
                                .maxConnecting(mongodbProperties.getPool().getMaxConnecting());
                    })


                    // ===== 压缩设置 =====
                    .compressorList(Arrays.asList(
                            MongoCompressor.createZlibCompressor(),
                            MongoCompressor.createSnappyCompressor(),
                            MongoCompressor.createZstdCompressor()
                    ))

                    // ===== 重试写入设置 =====
                    .retryWrites(true)
                    .retryReads(true)

                    // ===== 写入关注 =====
                    .writeConcern(WriteConcern.MAJORITY
                            .withJournal(true)
                            .withWTimeout(1000, TimeUnit.MILLISECONDS))

                    // ===== 读取首选项 =====
                    .readPreference(ReadPreference.primaryPreferred())

                    // ===== 读取关注 =====
                    .readConcern(ReadConcern.MAJORITY)
                    .build();
        }

        private static MappingMongoConverter createMongoConverter(MongoDatabaseFactory mongoDatabaseFactory) {
            // 创建自定义转换器列表
            // 时间转换器
            List<Converter<?, ?>> converters = new ArrayList<>(Jsr310Converters.getConvertersToRegister());
            // 创建自定义转换服务
            MongoCustomConversions conversions = new MongoCustomConversions(converters);
            // 创建映射上下文
            MongoMappingContext mappingContext = new MongoMappingContext();
            mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
            mappingContext.setAutoIndexCreation(true);
            mappingContext.afterPropertiesSet();

            // 创建DbRefResolver
            DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDatabaseFactory);

            // 创建并配置MappingMongoConverter
            MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
            converter.setCustomConversions(conversions);
            converter.setTypeMapper(new DefaultMongoTypeMapper(null)); // 禁用_class
            converter.afterPropertiesSet();

            return converter;
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
