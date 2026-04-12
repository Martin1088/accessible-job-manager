# ============================
# 1. Build stage
# ============================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

COPY pom.xml .

COPY AppClient/package*.json AppClient/

RUN mvn -B -q -DskipTests dependency:go-offline

COPY . .

RUN mvn -B -DskipTests clean package


# ============================
# 2. Runtime stage
# ============================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/LoginCourse-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8060

ENV JAVA_OPTS=""

# Start the app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]