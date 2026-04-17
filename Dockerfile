FROM eclipse-temurin:22-jre-alpine

WORKDIR /app

# Copy the pre-built JAR
COPY target/AgentMesh-1.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Health check with longer timeout for Spring Boot startup
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application with JAVA_OPTS support
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]

