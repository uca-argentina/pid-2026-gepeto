FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean install -DskipTests

FROM eclipse-temurin:21-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar server.jar

CMD ["java", "-jar", "server.jar"]
