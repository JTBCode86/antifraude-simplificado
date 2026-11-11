FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl analysis-service -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/analysis-service/target/analysis-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
