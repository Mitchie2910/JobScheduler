FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY target/jobscheduler.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]