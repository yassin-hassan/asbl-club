# --- Build stage: compile the app into an executable jar, then unpack it the way Class Data Sharing needs ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -B \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination /extracted

# --- Run stage: a small JRE image, tuned to start fast on a tiny server (Render's free tier: 0.1 CPU, 512 MB) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /extracted/ ./

# JVM settings for every java command below (the training run and the real start must match):
# - TieredStopAtLevel=1: only the quick JIT compiler. Less peak speed for long-running hot code, but far less CPU
#   spent compiling during startup, which is what a sleepy low-traffic server pays for.
# - UseSerialGC: one garbage-collector thread instead of several competing for a fraction of a CPU.
# - MaxRAMPercentage=75: use up to 3/4 of the container's memory for the heap (the default is 1/4).
# Measured with Docker limited to 0.2 CPU / 512 MB: 329 s → 161 s with these flags, → 97 s with CDS as well.
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:MaxRAMPercentage=75"

# Class Data Sharing: a training run starts the application context once, then the JVM saves the classes it
# loaded into app.jsa. Every real start maps that archive instead of loading and verifying thousands of classes.
# There is no database here, so the training run doesn't connect to one: a placeholder address (the pool only
# connects on first use), no migrations, and Hibernate told not to read the database's metadata.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
      -Dspring.datasource.url=jdbc:postgresql://training.invalid:5432/none -Dspring.sql.init.mode=never \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
