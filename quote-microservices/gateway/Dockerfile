# Build context: project root.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle/ gradle/
COPY proto/ proto/
COPY gateway/ gateway/
COPY inventory-service/ inventory-service/
COPY pricing-service/ pricing-service/
RUN ./gradlew --no-daemon -q :gateway:bootJar -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
COPY --from=build /src/gateway/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
