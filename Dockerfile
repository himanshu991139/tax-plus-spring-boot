# ==========================================
# Stage 1: Build the Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download dependencies (will be cached unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code and build the package (skips unit tests for faster deployment)
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Lightweight Runtime Environment
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the generated JAR file from the build stage
COPY --from=build /app/target/meesho-gstr1-converter-1.0.0.jar app.jar

# Render assigns a dynamic port via the PORT environment variable (defaults to 8080 if not set)
ENV PORT=8080
EXPOSE 8080

# Memory optimizations suitable for Render's free tier (512 MB RAM limit)
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-jar", "app.jar"]