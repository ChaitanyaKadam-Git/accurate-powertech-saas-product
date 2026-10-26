FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r app && mkdir /app/uploads && chown app /app/uploads
USER app
COPY --from=build /app/target/quote-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
