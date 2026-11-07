FROM node:20 AS ui
WORKDIR /ui
COPY minidc-ui/ .
RUN npm install && npm run build

FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
COPY --from=ui /ui/dist minidc-server/src/main/resources/static
RUN mvn -q -DskipTests -pl minidc-server -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/minidc-server/target/minidc-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx256m","-XX:+UseSerialGC","-jar","/app/app.jar"]

