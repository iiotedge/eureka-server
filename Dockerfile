# Use OpenJDK as base image
FROM openjdk:17
LABEL maintainer="santoshgndp@gmail.com"

# Set working directory
WORKDIR /app

# Copy JAR file from target folder
COPY target/eureka-server.jar eureka-server.jar

# Expose Eureka port
EXPOSE 8761

# Run Eureka Server
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
