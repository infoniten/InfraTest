# Build stage
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy all files
COPY . .

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy only the jar file from builder stage
COPY --from=builder /app/target/trade-consumer-db-1.0.0.jar app.jar

# Create logs directory
RUN mkdir -p logs

# Set JVM options for container
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport"

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]