# ===============================
# Multi-stage build for Credit Card Authorizer
# ===============================

# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy POM and download dependencies (cached layer)
COPY pom.xml /build/pom.xml
RUN mvn dependency:go-offline -B || true

# Copy source code
COPY src /build/src

# Build the application
RUN mvn clean package -DskipTests

# ===============================
# Runtime stage
# ===============================
FROM amazoncorretto:17-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from build stage
COPY --from=build /build/target/*.jar app.jar

# Change ownership
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser:appgroup

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8082/credit-card-authorizer/health || exit 1

# Run application with container-optimized JVM settings
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]