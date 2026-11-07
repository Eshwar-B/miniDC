FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -DskipTests -pl minidc-agent -am package

FROM eclipse-temurin:21-jre
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY --from=build /src/minidc-agent/target/minidc-agent-*.jar agent.jar
ENTRYPOINT ["java","-Xmx128m","-jar","/app/agent.jar"]
