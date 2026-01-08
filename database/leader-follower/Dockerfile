# Two stage build for faster deployment to AWS.

# =========================================================
# Stage 1: Build the Spring Boot application with Maven (Builder)
# =========================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration first (for dependency caching)
COPY pom.xml .

# Pre-download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Package the Spring Boot JAR (skip tests for faster builds)
RUN mvn clean package -DskipTests

# =========================================================
# Stage 2: Run the application in a lightweight image (Runtime)
# =========================================================
FROM eclipse-temurin:17-jre-jammy

# Create non-root user for security
RUN addgroup --gid 1000 appgroup && \
    adduser --uid 1000 --gid 1000 --disabled-password --gecos "" appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/leader-follower-w1r5-1.0.0.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port (can be overridden)
EXPOSE 8080

# Environment variable defaults (overridden by docker-compose)
ENV PORT=8080
ENV NODE_ID=1
ENV NODE_TYPE=leader

# Run the application
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]