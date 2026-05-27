FROM eclipse-temurin:21-jre-alpine

CMD ["java", "-jar","/opt/census-rm-notify-service.jar"]
COPY healthcheck.sh /opt/healthcheck.sh
# Create a system group and user without forcing UID/GID
RUN addgroup --system notifyservice && \
    adduser --system --ingroup notifyservice notifyservice

USER notifyservice

COPY target/census-rm-notify-service*.jar /opt/census-rm-notify-service.jar
