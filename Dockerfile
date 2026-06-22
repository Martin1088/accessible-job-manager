# ============================
# 1. Build stage
# ============================
FROM eclipse-temurin:21-jdk-jammy AS builder

# Install Node.js 20
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Cache Gradle wrapper + dependency resolution layer
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# Cache npm dependencies
COPY AppClient/package*.json AppClient/
RUN cd AppClient && npm ci

# Copy source and build (Gradle triggers npm build via processResources)
COPY . .
RUN ./gradlew build -x test


# ============================
# 2. Runtime stage
# ============================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /build/build/libs/accessible-job-manager-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8060

ENTRYPOINT ["java", "-jar", "/app/app.jar"]