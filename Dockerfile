# ==================================================
# STAGE 1: MAVEN DEPENDENCY CACHING & BUILD
# ==================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and cache maven dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source tree and compile jar skipping test executions
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==================================================
# STAGE 2: PRODUCTION RUNTIME ENVIRONMENT
# ==================================================
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Create a non-root system user and group for running the app securely
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy compiled jar artifact from build stage with correct permissions
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

# Define sensible defaults for environment variables
ENV SERVER_PORT=8081
# Optimize JRE memory usage, garbage collection, and random entropy source
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Expose server port (default 8081)
EXPOSE 8081

# Execute app via runtime entrypoint utilizing shell wrapper to expand env variables
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
