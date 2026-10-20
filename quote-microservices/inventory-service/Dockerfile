# Build context: project root.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle/ gradle/
COPY proto/ proto/
COPY gateway/ gateway/
COPY inventory-service/ inventory-service/
COPY pricing-service/ pricing-service/
RUN ./gradlew --no-daemon -q :inventory-service:installDist -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
COPY --from=build /src/inventory-service/build/install/inventory-service/ /app/
EXPOSE 8081 9091
ENTRYPOINT ["/app/bin/inventory-service"]
