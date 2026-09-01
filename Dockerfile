FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/log-doctor-*.jar /app/log-doctor.jar

EXPOSE 8080

ENV LOG_DOCTOR_BIND_ADDRESS=0.0.0.0 \
    LOG_DOCTOR_OLLAMA_URL=http://ollama:11434 \
    LOG_DOCTOR_OLLAMA_MODEL=qwen2.5:3b

ENTRYPOINT ["java", "-jar", "/app/log-doctor.jar", "--web"]
