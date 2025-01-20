# Build stage
FROM amazoncorretto:21-alpine AS build
WORKDIR /app

# Install necessary build dependencies
RUN apk add --no-cache bash

# Copy gradle files first
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Configure Gradle
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

# Copy source code
COPY src src

# Build with specific options
RUN ./gradlew build \
    --no-daemon \
    --console=plain \
    --no-parallel \
    --no-watch-fs \
    -Dorg.gradle.native=false

# Runtime stage
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]