FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY transaction-service/pom.xml transaction-service/pom.xml
COPY analysis-service/pom.xml analysis-service/pom.xml
COPY portal-service/pom.xml portal-service/pom.xml
RUN mvn -B -pl portal-service -am dependency:go-offline
COPY . .
RUN mvn -B -pl portal-service -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/portal-service/target/portal-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
