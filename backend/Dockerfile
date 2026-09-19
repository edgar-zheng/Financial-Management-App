# Build from the repository root:
# docker build -f backend/Dockerfile -t portfolio-backend:local .
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY mvnw ./mvnw
COPY .mvn/wrapper/maven-wrapper.properties ./.mvn/wrapper/maven-wrapper.properties
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src/main ./backend/src/main

# Integration tests run separately against a disposable MySQL database.
RUN sh ./mvnw -B -ntp -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/target/portfolio-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
