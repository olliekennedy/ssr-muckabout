# Stage 1: Build
FROM gradle:8.4-jdk21 AS builder
COPY . /app
WORKDIR /app
RUN ./gradlew shadowJar

# Stage 2: Run
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /app/build/libs/LastTrainHome-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
