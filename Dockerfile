FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/vocabexam-1.0.0.jar app.jar
RUN mkdir -p /app/uploads /app/pdfs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
