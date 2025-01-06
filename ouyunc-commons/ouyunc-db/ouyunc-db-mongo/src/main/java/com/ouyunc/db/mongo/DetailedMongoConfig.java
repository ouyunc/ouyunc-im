//package com.ouyunc.db.mongo;
//
//public class DetailedMongoConfig {
//    private static volatile MongoTemplate mongoTemplate;
//
//    public static MongoTemplate getMongoTemplate() {
//        if (mongoTemplate == null) {
//            synchronized (DetailedMongoConfig.class) {
//                if (mongoTemplate == null) {
//                    mongoTemplate = createMongoTemplate();
//                }
//            }
//        }
//        return mongoTemplate;
//    }
//
//    private static MongoTemplate createMongoTemplate() {
//        // 创建MongoDB客户端配置
//        MongoClientSettings settings = createMongoClientSettings();
//        MongoClient mongoClient = MongoClients.create(settings);
//
//        // 创建自定义转换器
//        MappingMongoConverter converter = createMongoConverter(mongoClient);
//
//        // 创建并配置MongoTemplate
//        MongoTemplate template = new MongoTemplate(mongoClient, "your_database", converter);
//
//        // 设置写入关注
//        template.setWriteConcern(WriteConcern.MAJORITY);
//
//        // 设置读取首选项
//        template.setReadPreference(ReadPreference.primary());
//
//        // 设置写入结果检查模式
//        template.setWriteResultChecking(WriteResultChecking.EXCEPTION);
//
//        return template;
//    }
//
//    private static MongoClientSettings createMongoClientSettings() {
//        Properties props = loadProperties();
//
//        return MongoClientSettings.builder()
//            // ===== 基本连接配置 =====
//            .applyConnectionString(new ConnectionString(props.getProperty("mongodb.uri")))
//
//            // ===== 连接池配置 =====
//            .applyToConnectionPoolSettings(builder -> {
//                builder.maxSize(Integer.parseInt(props.getProperty("mongodb.pool.maxSize", "100")))
//                       .minSize(Integer.parseInt(props.getProperty("mongodb.pool.minSize", "10")))
//                       .maxWaitTime(Long.parseLong(props.getProperty("mongodb.pool.maxWaitTime", "5000")),
//                                  TimeUnit.MILLISECONDS)
//                       .maxConnectionLifeTime(Long.parseLong(props.getProperty("mongodb.pool.maxLifeTime", "1800000")),
//                                           TimeUnit.MILLISECONDS)
//                       .maxConnectionIdleTime(Long.parseLong(props.getProperty("mongodb.pool.maxIdleTime", "600000")),
//                                           TimeUnit.MILLISECONDS)
//                       .maintenanceInitialDelay(0, TimeUnit.MILLISECONDS)
//                       .maintenanceFrequency(1, TimeUnit.MINUTES)
//                       .maxConnecting(2)
//                       .pendingConnectionTimeout(Duration.ofSeconds(30));
//            })
//
//            // ===== 服务器设置 =====
//            .applyToServerSettings(builder -> {
//                builder.heartbeatFrequency(10, TimeUnit.SECONDS)
//                       .minHeartbeatFrequency(500, TimeUnit.MILLISECONDS);
//            })
//
//            // ===== 套接字设置 =====
//            .applyToSocketSettings(builder -> {
//                builder.connectTimeout(Integer.parseInt(props.getProperty("mongodb.socket.connectTimeout", "10000")),
//                                    TimeUnit.MILLISECONDS)
//                       .readTimeout(Integer.parseInt(props.getProperty("mongodb.socket.readTimeout", "15000")),
//                                  TimeUnit.MILLISECONDS)
//                       .receiveBufferSize(1024 * 1024)
//                       .sendBufferSize(1024 * 1024);
//            })
//
//            // ===== SSL设置 =====
//            .applyToSslSettings(builder -> {
//                builder.enabled(Boolean.parseBoolean(props.getProperty("mongodb.ssl.enabled", "false")))
//                       .invalidHostNameAllowed(Boolean.parseBoolean(props.getProperty("mongodb.ssl.invalidHostAllowed", "false")))
//                       .context(createSSLContext());
//            })
//
//            // ===== 集群设置 =====
//            .applyToClusterSettings(builder -> {
//                builder.hosts(Arrays.asList(
//                    new ServerAddress("localhost", 27017),
//                    new ServerAddress("localhost", 27018)
//                ))
//                .mode(ClusterConnectionMode.MULTIPLE)
//                .serverSelectionTimeout(5, TimeUnit.SECONDS)
//                .localThreshold(15, TimeUnit.MILLISECONDS);
//            })
//
//            // ===== 压缩设置 =====
//            .compressorList(Arrays.asList(
//                MongoCompressor.createZlibCompressor(),
//                MongoCompressor.createSnappyCompressor(),
//                MongoCompressor.createZstdCompressor()
//            ))
//
//            // ===== 重试写入设置 =====
//            .retryWrites(true)
//            .retryReads(true)
//
//            // ===== 写入关注 =====
//            .writeConcern(WriteConcern.MAJORITY
//                .withJournal(true)
//                .withWTimeout(1000, TimeUnit.MILLISECONDS))
//
//            // ===== 读取首选项 =====
//            .readPreference(ReadPreference.primaryPreferred())
//
//            // ===== 读取关注 =====
//            .readConcern(ReadConcern.MAJORITY)
//
//            .build();
//    }
//
//    private static MappingMongoConverter createMongoConverter(MongoClient mongoClient) {
//        // 创建自定义转换器列表
//        List<Converter<?, ?>> converters = new ArrayList<>();
//        converters.add(new DateToLocalDateTimeConverter());
//        converters.add(new LocalDateTimeToDateConverter());
//
//        // 创建自定义转换服务
//        MongoCustomConversions conversions = new MongoCustomConversions(converters);
//
//        // 创建映射上下文
//        MongoMappingContext mappingContext = new MongoMappingContext();
//        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
//        mappingContext.setAutoIndexCreation(true);
//        mappingContext.afterPropertiesSet();
//
//        // 创建DbRefResolver
//        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoClient);
//
//        // 创建并配置MappingMongoConverter
//        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
//        converter.setCustomConversions(conversions);
//        converter.setTypeMapper(new DefaultMongoTypeMapper(null)); // 禁用_class
//        converter.afterPropertiesSet();
//
//        return converter;
//    }
//
//    private static SSLContext createSSLContext() {
//        try {
//            // 加载信任库
//            KeyStore trustStore = KeyStore.getInstance("JKS");
//            try (InputStream is = new FileInputStream("truststore.jks")) {
//                trustStore.load(is, "truststore_password".toCharArray());
//            }
//
//            // 创建信任管理器
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            tmf.init(trustStore);
//
//            // 加载密钥库
//            KeyStore keyStore = KeyStore.getInstance("JKS");
//            try (InputStream is = new FileInputStream("keystore.jks")) {
//                keyStore.load(is, "keystore_password".toCharArray());
//            }
//
//            // 创建密钥管理器
//            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
//            kmf.init(keyStore, "key_password".toCharArray());
//
//            // 创建并配置SSL上下文
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
//
//            return sslContext;
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to create SSL context", e);
//        }
//    }
//
//    private static Properties loadProperties() {
//        Properties props = new Properties();
//        try (InputStream input = DetailedMongoConfig.class.getClassLoader()
//                .getResourceAsStream("mongodb.properties")) {
//            if (input == null) {
//                throw new RuntimeException("Unable to find mongodb.properties");
//            }
//            props.load(input);
//        } catch (IOException e) {
//            throw new RuntimeException("Error loading mongodb.properties", e);
//        }
//        return props;
//    }
//
//    // 自定义转换器
//    private static class DateToLocalDateTimeConverter implements Converter<Date, LocalDateTime> {
//        @Override
//        public LocalDateTime convert(Date source) {
//            return LocalDateTime.ofInstant(source.toInstant(), ZoneId.systemDefault());
//        }
//    }
//
//    private static class LocalDateTimeToDateConverter implements Converter<LocalDateTime, Date> {
//        @Override
//        public Date convert(LocalDateTime source) {
//            return Date.from(source.atZone(ZoneId.systemDefault()).toInstant());
//        }
//    }
//}