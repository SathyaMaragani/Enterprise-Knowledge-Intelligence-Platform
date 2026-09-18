# syntax=docker/dockerfile:1
# Build context is subjects/, because the backend compiles the TextHack engine
# from subjects/DSA-3 (see pom.xml). Two targets:
#
#   runtime   the model is mounted at run time (docker/docker-compose.yml)
#   render    the default: sized for Render's free instance (512 MB, 0.1 CPU),
#             see deploy/RENDER-FREE-COMPATIBILITY.md
#
#   docker build -f DBE-DSD/backend/Dockerfile [--target runtime] subjects
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY DSA-3/texthack DSA-3/texthack
COPY DBE-DSD/backend/pom.xml DBE-DSD/backend/pom.xml
COPY DBE-DSD/backend/src DBE-DSD/backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -f DBE-DSD/backend/pom.xml -DskipTests package

# The pinned, checksum-verified MiniLM model; only built for the render target.
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

# At 512 MB the untuned image is OOM-killed while loading the model. What fits:
# a fixed 128 MB heap (the live set is about 55 MB), C1 only (less JIT memory and
# CPU), and two malloc arenas instead of one per thread. The jar is extracted and
# a class-data-sharing archive is trained at build time; together they cut startup
# at 0.1 CPU from 148 s to about 100 s and resident memory by about 40 MB. The
# archive only matches this JVM, these flags and this extracted jar.
FROM eclipse-temurin:21-jre AS render
RUN useradd --system --create-home --uid 10001 eip
COPY --from=model /models/minilm /models/minilm
ENV SPRING_PROFILES_ACTIVE=prod \
    EMBEDDING_ENABLED=true \
    EMBEDDING_MODELPATH=/models/minilm/onnx/model.onnx \
    EMBEDDING_TOKENIZERPATH=/models/minilm/tokenizer.json \
    JAVA_TOOL_OPTIONS="-Xmx128m -XX:TieredStopAtLevel=1" \
    MALLOC_ARENA_MAX=2
WORKDIR /app
# Training run: start the context up to refresh, record the loaded classes, exit.
# There are no databases at build time, so Hibernate skips schema validation and
# JDBC metadata; the startup runners (bootstrap admin, Qdrant collection) run
# only after refresh and are not reached.
RUN --mount=type=bind,from=build,source=/src/DBE-DSD/backend/target,target=/target \
    cp /target/*.jar /tmp/app.jar \
 && java -Djarmode=tools -jar /tmp/app.jar extract --destination /app \
 && rm /tmp/app.jar \
 && JWT_SECRET="$(head -c 48 /dev/urandom | base64 -w0)" \
    java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
         -Dspring.jpa.hibernate.ddl-auto=none \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -jar app.jar \
 && test -s app.jsa
USER eip
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
