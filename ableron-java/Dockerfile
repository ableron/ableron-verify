FROM gradle:7-jdk19 as builder
COPY --chown=gradle:gradle . /opt/app
WORKDIR /opt/app
RUN gradle -q bootJar

FROM eclipse-temurin:19-jre-alpine
COPY --from=builder /opt/app/build/libs /opt/app
WORKDIR /opt/app

ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
