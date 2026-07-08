# 多阶段构建：JDK 编译 → JRE 运行
# 构建：docker build -t ouyunc-im:latest .
# 运行：docker compose up -d
# 100 万连接：宿主机 64G+、32 核+；堆默认 32G，小规格请 -e JAVA_OPTS 覆盖

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

COPY . .

ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

RUN mvn -B -q -pl ouyunc-server -am package -DskipTests

# --- runtime ---
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 10001 ouyunc \
    && useradd --system --uid 10001 --gid ouyunc --home-dir /app --create-home ouyunc

WORKDIR /app

COPY --from=build --chown=ouyunc:ouyunc /build/ouyunc-server/target/ouyunc-im-server.jar .

USER ouyunc

EXPOSE 6003

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -Xms32g -Xmx32g -XX:MaxDirectMemorySize=8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

# 配置默认来自 jar 内 classpath：ouyunc-server.yml（改中间件地址需重建镜像或自定义镜像层覆盖 resources）
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar ouyunc-im-server.jar"]
