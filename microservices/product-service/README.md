# Product Service - Spring Boot Server

## Overview
RESTful API server for handling product creation requests. Deployed on AWS ECS as a Docker container.

## ECR Image URL
Run this command to get your ECR image URL:

```bash
aws ecr describe-repositories \
  --repository-names product-service \
  --region us-east-1 \
  --query 'repositories[0].repositoryUri' \
  --output text
```

## ECR IMAGE URL
637423169516.dkr.ecr.us-east-1.amazonaws.com/product-service

### Update the server url in LoadTestClient.java
```java
private static final String SERVER_URL = "http://54.227.106.63:8080";
```

### Build and Run
```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

## Test the Deployed Server
```bash
Mvn clean compile

mvn exec:java -Dexec.mainClass="com.cs6650.client.LoadTestClient"
```

## Requirements
- Java 17
- Spring Boot 3.5.6
- Maven 3.9+

