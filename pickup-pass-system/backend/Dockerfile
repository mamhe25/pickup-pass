# Multi-stage build: compile with full Maven+JDK, run on a slim JRE.
# Cloud Run injects the PORT env var at runtime — application.yml's
# server.port must respect it (see the ${PORT:8080} default there).

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Copy pom.xml first so dependency resolution is cached across builds
# unless pom.xml itself changes — speeds up repeat deploys significantly.
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Cloud Run always sets PORT; other hosts (Render, Railway, etc.) usually do too.
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
