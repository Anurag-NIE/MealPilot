# syntax=docker/dockerfile:1

FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY mvnw.ps1 mvnw.ps1

# Fetch dependencies first for better layer caching
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src/ src/

RUN mvn -q -DskipTests package


FROM eclipse-temurin:21-jre
WORKDIR /app

ENV PORT=9000
EXPOSE 9000

COPY --from=build /workspace/target/*.jar /app/app.jar

# Default to prod inside containers; override as needed.
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-jar","/app/app.jar"]
