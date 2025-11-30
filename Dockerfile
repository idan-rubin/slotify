FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY slotify-core ./slotify-core
COPY slotify-app ./slotify-app
COPY slotify-web ./slotify-web
RUN apt-get update && apt-get install -y maven && mvn -pl slotify-web -am package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/slotify-web/target/slotify-web-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
