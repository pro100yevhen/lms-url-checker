# Build stage (use valid Gradle tag)
FROM gradle:8.5-jdk21-jammy AS build
WORKDIR /app

# Cache Gradle dependencies
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

# Build application
COPY src ./src
RUN gradle build --no-daemon --parallel -x test

# Runtime stage
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]