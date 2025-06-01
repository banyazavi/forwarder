FROM eclipse-temurin:17.0.15_6-jre-noble

WORKDIR /app

COPY build/libs/forwarder.jar forwarder.jar

ENV ACCESS_LOGGING_ENABLED=false
ENV FORWARDER_TARGET_HOST=
ENV SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "forwarder.jar"]
