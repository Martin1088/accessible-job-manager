# ============================
# 1. Build stage
# ============================
FROM eclipse-temurin:26-jdk-jammy AS builder

# The version cannot be derived inside this stage: `.dockerignore` excludes
# `.git` so that `COPY . .` does not bake the repository into a layer, and
# `git describe` needs exactly that. So the release workflow passes the tag in,
# and the default matches the fallback in `build.gradle` - the two have to
# agree, because the jar is copied out by name below.
ARG APP_VERSION=0.0.0-SNAPSHOT

# Install Node.js 24
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
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
RUN ./gradlew build -x test -Pversion=${APP_VERSION}


# ============================
# 2. Runtime stage
# ============================
FROM eclipse-temurin:26-jre-jammy

WORKDIR /app

ARG APP_VERSION=0.0.0-SNAPSHOT

# By name, not by wildcard: `build` also produces the `-plain.jar`, and a
# wildcard that matches two files fails with a message about the destination
# rather than about the version.
COPY --from=builder /build/build/libs/accessible-job-manager-${APP_VERSION}.jar app.jar

EXPOSE 8060

ENTRYPOINT ["java", "-jar", "/app/app.jar"]