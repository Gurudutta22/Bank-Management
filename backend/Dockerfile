# =====================================================================================
# Backend image - multi-stage.
#
# Stage 1 compiles with a full JDK + Maven; stage 2 ships only a JRE and the jar. The
# build tooling never reaches the running container, which cuts the image from ~800MB to
# ~200MB and removes a large amount of software from the attack surface.
# =====================================================================================

# ------------------------------------------------------------------ build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM alone first. Docker caches each layer, so dependency download is only
# repeated when the POM itself changes - not on every source edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as an unprivileged user. If the process is ever compromised, the attacker does not
# start out as root inside the container.
RUN addgroup -S novabank && adduser -S novabank -G novabank

COPY --from=build /build/target/*.jar app.jar
RUN chown -R novabank:novabank /app
USER novabank

EXPOSE 8080

# Compose waits on this before routing traffic to the container.
HEALTHCHECK --interval=20s --timeout=4s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then sizes its heap from the
# container's memory limit, so changing the limit does not require rebuilding the image.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
