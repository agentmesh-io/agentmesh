FROM eclipse-temurin:22-jre-alpine

WORKDIR /app

# Copy the pre-built Spring Boot JAR (version follows pom.xml)
ARG JAR_FILE=target/AgentMesh-1.0.0.jar
COPY ${JAR_FILE} app.jar

# Expose port
EXPOSE 8081

# Health check with longer timeout for Spring Boot startup
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Run the application with JAVA_OPTS support
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]

