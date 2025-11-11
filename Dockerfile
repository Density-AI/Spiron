# ====== Build stage ======
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean shadowJar

# ====== Runtime stage ======
FROM eclipse-temurin:25-jre-alpine
ENV JAVA_OPTS="-Xms256m -Xmx512m"
# Use environment names that match SpironConfig expectations
ENV spiron_NODE_ID=""
ENV spiron_PORT="8080"
ENV spiron_data_dir="/var/lib/spiron"
RUN addgroup -S spiron && adduser -S spiron -G spiron
RUN mkdir -p /var/lib/spiron && chown -R spiron:spiron /var/lib/spiron
USER spiron:spiron
WORKDIR /app
COPY --from=build /src/build/libs/spiron.jar /app/spiron.jar
EXPOSE 8080 9090
RUN apk add --no-cache curl
HEALTHCHECK --interval=20s --timeout=3s --start-period=20s \
  CMD curl -fsS http://127.0.0.1:9090/metrics >/dev/null || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/spiron.jar"]
VOLUME ["/var/lib/spiron"]
