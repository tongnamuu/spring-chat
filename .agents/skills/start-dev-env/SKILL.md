---
name: start-dev-env
description: Start the spring-chat multi-module application and full Docker infrastructure (Nginx 80 port, Postgres, Kafka, Redis, Java 25 microservices) for local development, testing, and debugging.
---

# Start Dev Environment Skill (`start-dev-env`)

이 스킬은 `spring-chat` 애플리케이션의 개발 및 테스트를 위해 Java 25 빌드와 전체 도커 인프라(Nginx 로드밸런서, PostgreSQL, Kafka, Redis, Spring Boot 마이크로서비스)를 한 번에 기동하고 검증합니다.

## 🚀 Execution Steps

1. **자동 실행 스크립트 실행**:
   ```bash
   bash .agents/skills/start-dev-env/scripts/start.sh
   ```

2. **서비스 상태 검증**:
   ```bash
   docker compose ps
   ```
   다음 컨테이너들이 모두 `Up` 또는 `healthy` 상태이어야 합니다:
   - `spring-chat-nginx` (`0.0.0.0:80->80/tcp`)
   - `spring-chat-api`
   - `spring-chat-ws`
   - `spring-chat-consumer`
   - `spring-chat-postgres` (`healthy`)
   - `spring-chat-kafka`
   - `spring-chat-redis`

3. **웹 접속 확인**:
   - 웹 대시보드 URL: `http://localhost/`

## 🧹 Shutdown Command
개발 환경을 종료하고 정리할 때 사용합니다:
```bash
docker compose down
```
