FROM openjdk:8-jdk-alpine

COPY target/chairwise-output-service-0.0.1-SNAPSHOT.jar /home/app.jar
ENTRYPOINT ["java","-jar","/home/app.jar"]