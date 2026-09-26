# syntax=docker/dockerfile:1
#
# idlefish-backend 容器镜像（F-15.3 容器化与发布）
# 多阶段构建：构建阶段用 Maven 出 jar；运行阶段用 JRE 精简镜像。
#
# 构建（docker build 环境默认联网拉取依赖；如需内网代理镜像可改 FROM / 加 -o 依赖缓存层）：
#   docker build -t idlefish-backend:1.0.0 ./idlefish-backend
# 运行：
#   docker run -p 8080:8080 \
#     -e SPRING_PROFILES_ACTIVE=docker \
#     -e JWT_SECRET='<>=32字节密钥' \
#     idlefish-backend:1.0.0

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 利用层缓存（依赖不变则不重下）
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
# docker build 环境默认联网拉取（无离线 .m2 缓存）；跳过测试（CI 已跑全量；沙箱无 MySQL 集成测试）
RUN mvn -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 探活依赖 curl（ObservabilityController 零依赖提供 /actuator/health）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC" \
    SPRING_PROFILES_ACTIVE=docker

COPY --from=build /app/target/idlefish-backend-1.0.0.jar app.jar

EXPOSE 8080

# 容器探针：/actuator/health 由零依赖 ObservabilityController 提供（无需 actuator 依赖）
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
