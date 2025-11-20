FROM gradle:8.5-jdk17 AS builder

WORKDIR /app
COPY . .

RUN gradle clean shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine

WORKDIR /spiron

COPY --from=builder /app/build/libs/spiron-server.jar /spiron/spiron-server.jar

ENV SPIRON_NODE_ID=node-0
ENV SPIRON_PORT=8080
ENV SPIRON_METRICS_PORT=9090
ENV SPIRON_DATA_DIR=/var/lib/spiron

EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "/spiron/spiron-server.jar"]
