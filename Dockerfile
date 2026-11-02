# Stage 1: Build the JAR package using Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY support-crm/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build JAR (tests run in .github/workflows/ci.yml)
COPY support-crm/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image with Java 21 JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Indian Standard Time (IST)
RUN apk add --no-cache tzdata
ENV TZ="Asia/Kolkata"

# Run as a normal user, so a break-in does not get root inside the container
RUN addgroup -S crm && adduser -S crm -G crm
USER crm

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Kolkata"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

# exec makes Java the main process, so it receives the stop signal and shuts down cleanly
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
