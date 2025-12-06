# ===============================
# Stage 1: Build stage using Maven
# ===============================
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory inside the container
WORKDIR /app

# Copy Maven configuration to leverage Docker cache for dependencies
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline

# Copy source code
COPY src ./src

# Build the JAR (skip tests for faster build)
RUN mvn clean package -DskipTests

# ===============================
# Stage 2: Runtime stage
# ===============================
FROM openjdk:17-jdk-slim

# Create non-root user for security
RUN addgroup --gid 1000 spring \
    && adduser --uid 1000 --gid 1000 --shell /bin/sh --disabled-password --gecos "" spring

# Set working directory
WORKDIR /app

# Copy the JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Adjust ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring

# Expose the application port (optional but helpful)
EXPOSE 8082

# JVM memory settings (tweak as needed)
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
