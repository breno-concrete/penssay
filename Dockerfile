# Build stage: compiles the jar with the Maven wrapper, so the host needs nothing but Docker
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# a Windows checkout gives mvnw CRLF line endings, which Linux cannot run
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests && cp target/penssay-*.jar app.jar

# Run stage: only the JRE and the jar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 penssay
COPY --from=build /app/app.jar app.jar
USER penssay
EXPOSE 8080
# use most of the machine's memory for the heap; the container limit is the real ceiling
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# OPENROUTER_API_KEY comes from the host's secrets at run time, never from this file or the image
ENTRYPOINT ["java", "-jar", "app.jar"]
