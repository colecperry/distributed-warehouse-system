# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM openjdk:17.0.1-jdk-slim

# Create non-root user for security
RUN addgroup --system spring && adduser --system --group spring

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

USER spring

# JVM settings for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]