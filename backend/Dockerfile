# Backend image: built with the Maven wrapper, run as an unprivileged user on a JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies first, so they are cached until pom.xml changes.
RUN ./mvnw -B -ntp -q dependency:resolve
COPY src src
RUN ./mvnw -B -ntp -q -DskipTests package \
 && java -Djarmode=tools -jar target/fontis-backend.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 fontis
WORKDIR /app
# Layers from least to most often changed, for smaller image updates.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER fontis
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
