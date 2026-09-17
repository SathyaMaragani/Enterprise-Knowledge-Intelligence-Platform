# syntax=docker/dockerfile:1
# Build context is subjects/, because the backend compiles the TextHack engine
# from subjects/DSA-3 (see pom.xml). Two targets:
#
#   runtime   the model is mounted at run time (docker/docker-compose.yml)
#   cloudrun  the default: the model is baked in and the prod profile is on,
#             for platforms without host volumes (Cloud Run)
#
#   docker build -f DBE-DSD/backend/Dockerfile [--target runtime] subjects
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY DSA-3/texthack DSA-3/texthack
COPY DBE-DSD/backend/pom.xml DBE-DSD/backend/pom.xml
COPY DBE-DSD/backend/src DBE-DSD/backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f DBE-DSD/backend/pom.xml -DskipTests package

# The pinned, checksum-verified MiniLM model; only built for the cloudrun target.
FROM alpine:3.20 AS model
RUN apk add --no-cache curl
COPY DBE-DSD/backend/model/fetch-model.sh /fetch-model.sh
RUN sh /fetch-model.sh /models/minilm

# glibc base, not Alpine: ONNX Runtime and the DJL tokenizer ship glibc natives.
FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --create-home --uid 10001 eip
WORKDIR /app
COPY --from=build /src/DBE-DSD/backend/target/*.jar app.jar
USER eip
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

FROM runtime AS cloudrun
COPY --from=model /models/minilm /models/minilm
ENV SPRING_PROFILES_ACTIVE=prod \
    EMBEDDING_ENABLED=true \
    EMBEDDING_MODELPATH=/models/minilm/onnx/model.onnx \
    EMBEDDING_TOKENIZERPATH=/models/minilm/tokenizer.json
