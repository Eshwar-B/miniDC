# Multi-stage Dockerfile for minidc-server
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom files
COPY mvn.cmd ./
COPY pom.xml ./
COPY minidc-common/pom.xml minidc-common/
COPY minidc-server/pom.xml minidc-server/
COPY minidc-agent/pom.xml minidc-agent/

# Download dependencies
RUN ./mvn.cmd dependency:go-offline -B --no-transfer-progress

# Copy source code
COPY minidc-common/src minidc-common/src
COPY minidc-server/src minidc-server/src

# Build the server jar
RUN ./mvn.cmd clean package -pl minidc-server -am -DskipTests -B --no-transfer-progress

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1000 minidc && adduser -D -u 1000 -G minidc minidc

# Copy built jar
COPY --from=builder /app/minidc-server/target/minidc-server-*.jar app.jar

# Set ownership
RUN chown -R minidc:minidc /app

USER minidc

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# Expose ports
EXPOSE 8080

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
