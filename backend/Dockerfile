FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradlew ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
