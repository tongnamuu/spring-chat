#!/usr/bin/env bash
set -e

echo "=== [1/2] Building Spring Boot 3.3 Java 25 JARs ==="
export JAVA_HOME=/Users/taehyeongban/.sdkman/candidates/java/21.0.10-tem
export PATH=$JAVA_HOME/bin:$PATH
./gradlew build -x test

echo "=== [2/2] Launching Docker Compose Stack (Nginx port 80, Postgres, Kafka, Redis) ==="
docker compose up -d --build

echo "=== Services Status ==="
docker compose ps

echo "🚀 Development environment is ready! Access UI at http://localhost/"
