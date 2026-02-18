FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle /app/
COPY src /app/src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /opt/scouter/agent
COPY --from=build /app/build/libs/*.jar /app/app.jar
COPY src/main/resources/scouter.conf /opt/scouter/agent/scouter.conf
ENV JAVA_TOOL_OPTIONS="-Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]
