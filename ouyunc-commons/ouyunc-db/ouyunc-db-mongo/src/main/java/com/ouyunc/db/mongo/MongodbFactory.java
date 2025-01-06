package com.ouyunc.db.mongo;

import com.fasterxml.jackson.databind.util.Converter;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.db.mongo.properties.MongodbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.WriteResultChecking;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
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
            MongoTemplate mongoTemplate = null;
            if (mongoTemplateMap.get(databaseName) == null) {
                synchronized (MongodbFactory.class) {
                    if (mongoTemplateMap.get(databaseName) == null) {
                        mongoTemplateMap.put(databaseName, mongoTemplate = createMongoTemplate(databaseName));
                    }
                }
            }
            if (mongoTemplate == null) {
                log.error("");
                throw new RuntimeException("");
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
            Properties props = loadProperties();

            return MongoClientSettings.builder()
                    // ===== 基本连接配置 =====
                    .applyConnectionString(new ConnectionString(props.getProperty("mongodb.uri")))

                    // ===== 连接池配置 =====
                    .applyToConnectionPoolSettings(builder -> {
                        builder.maxSize(Integer.parseInt(props.getProperty("mongodb.pool.maxSize", "100")))
                                .minSize(Integer.parseInt(props.getProperty("mongodb.pool.minSize", "10")))
                                .maxWaitTime(Long.parseLong(props.getProperty("mongodb.pool.maxWaitTime", "5000")),
                                        TimeUnit.MILLISECONDS)
                                .maxConnectionLifeTime(Long.parseLong(props.getProperty("mongodb.pool.maxLifeTime", "1800000")),
                                        TimeUnit.MILLISECONDS)
                                .maxConnectionIdleTime(Long.parseLong(props.getProperty("mongodb.pool.maxIdleTime", "600000")),
                                        TimeUnit.MILLISECONDS)
                                .maintenanceInitialDelay(0, TimeUnit.MILLISECONDS)
                                .maintenanceFrequency(1, TimeUnit.MINUTES)
                                .maxConnecting(2)
                                .pendingConnectionTimeout(Duration.ofSeconds(30));
                    })

                    // ===== 服务器设置 =====
                    .applyToServerSettings(builder -> {
                        builder.heartbeatFrequency(10, TimeUnit.SECONDS)
                                .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS);
                    })

                    // ===== 套接字设置 =====
                    .applyToSocketSettings(builder -> {
                        builder.connectTimeout(Integer.parseInt(props.getProperty("mongodb.socket.connectTimeout", "10000")),
                                        TimeUnit.MILLISECONDS)
                                .readTimeout(Integer.parseInt(props.getProperty("mongodb.socket.readTimeout", "15000")),
                                        TimeUnit.MILLISECONDS)
                                .receiveBufferSize(1024 * 1024)
                                .sendBufferSize(1024 * 1024);
                    })

                    // ===== SSL设置 =====
                    .applyToSslSettings(builder -> {
                        builder.enabled(Boolean.parseBoolean(props.getProperty("mongodb.ssl.enabled", "false")))
                                .invalidHostNameAllowed(Boolean.parseBoolean(props.getProperty("mongodb.ssl.invalidHostAllowed", "false")))
                                .context(createSSLContext());
                    })

                    // ===== 集群设置 =====
                    .applyToClusterSettings(builder -> {
                        builder.hosts(Arrays.asList(
                                        new ServerAddress("localhost", 27017),
                                        new ServerAddress("localhost", 27018)
                                ))
                                .mode(ClusterConnectionMode.MULTIPLE)
                                .serverSelectionTimeout(5, TimeUnit.SECONDS)
                                .localThreshold(15, TimeUnit.MILLISECONDS);
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
            List<Converter<?, ?>> converters = new ArrayList<>();
            converters.add(new DetailedMongoConfig.DateToLocalDateTimeConverter());
            converters.add(new DetailedMongoConfig.LocalDateTimeToDateConverter());

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

        private static SSLContext createSSLContext() {
            try {
                // 加载信任库
                KeyStore trustStore = KeyStore.getInstance("JKS");
                try (InputStream is = new FileInputStream("truststore.jks")) {
                    trustStore.load(is, "truststore_password".toCharArray());
                }

                // 创建信任管理器
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                // 加载密钥库
                KeyStore keyStore = KeyStore.getInstance("JKS");
                try (InputStream is = new FileInputStream("keystore.jks")) {
                    keyStore.load(is, "keystore_password".toCharArray());
                }

                // 创建密钥管理器
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "key_password".toCharArray());

                // 创建并配置SSL上下文
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

                return sslContext;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create SSL context", e);
            }
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
