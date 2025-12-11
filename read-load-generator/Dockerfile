FROM maven:3.9-eclipse-temurin-11 AS build

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:11-jre

WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/target/read-load-generator-1.0.0.jar app.jar

# Set JVM options
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Expose actuator port
EXPOSE 8095

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
