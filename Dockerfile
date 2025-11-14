# Multi-stage build for Spring Boot application

# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Create directory for H2 database
RUN mkdir -p /data

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose application port
EXPOSE 8080

# Health check - using the H2 console endpoint as a basic health indicator
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/h2-console || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

