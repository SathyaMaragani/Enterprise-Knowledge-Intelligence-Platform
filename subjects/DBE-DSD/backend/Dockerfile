# syntax=docker/dockerfile:1
# Build context is subjects/, because the backend compiles the TextHack engine
# from subjects/DSA-3 (see pom.xml). From subjects/DBE-DSD/docker, compose does:
#   docker build -f DBE-DSD/backend/Dockerfile ../..
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY DSA-3/texthack DSA-3/texthack
COPY DBE-DSD/backend/pom.xml DBE-DSD/backend/pom.xml
COPY DBE-DSD/backend/src DBE-DSD/backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f DBE-DSD/backend/pom.xml -DskipTests package

# glibc base, not Alpine: ONNX Runtime and the DJL tokenizer ship glibc natives.
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 eip
WORKDIR /app
COPY --from=build /src/DBE-DSD/backend/target/*.jar app.jar
USER eip
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
