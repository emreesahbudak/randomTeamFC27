# Paths below are relative to the monorepo ROOT, not backend/ — Railway's config-as-code
# paths (see railway.json at the repo root) are always repo-root-relative regardless of any
# dashboard "Root Directory" setting, so the build context here is the whole repo.
#
# Multi-stage build: compile with Maven + JDK, run on a slim JRE — keeps the deployed
# image small and doesn't need Maven/JDK installed on Railway's build machine at all.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
# git on Windows doesn't reliably preserve the executable bit, so mvnw can land here
# without it regardless of what's committed — set it explicitly rather than relying on that.
RUN chmod +x mvnw
RUN ./mvnw -q dependency:go-offline
COPY backend/src/ src/
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
