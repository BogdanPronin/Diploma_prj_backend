FROM openjdk:21-jdk-slim

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY gradlew ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew build -x test

EXPOSE 8080

CMD ["java", "-jar", "build/libs/Backend_diploma-1.0-SNAPSHOT.jar"]
