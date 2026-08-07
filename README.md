# Spring 기반 무중단 대용량 채팅 시스템 (`spring-chat`)

PostgreSQL, Apache Kafka, Apache Flink, Redis 및 Spring Boot 3.x 기반의 고성능, 확장 가능한 무중단 대용량 실시간 채팅 시스템입니다.

---

## 🛠️ 주요 요구사항 및 특징

1. **최대 50명 동시 대화 그룹 채팅방**
   - 방 생성 시 최대 50명 제한 검증 (`maxCapacity` <= 50).
   - Redis Atomic Counter 및 PostgreSQL 조건부 수량 업데이트로 동시성 초과 방지.
2. **1대1 개인 채팅 (DIRECT)**
   - 최대 인원 2명 자동 고정 및 상대방 초대 연동.
3. **공개(PUBLIC) & 비공개(PRIVATE) 채팅방**
   - 방 종류별 검색 및 입장 제어.
4. **초대 코드(Invite Code) 기반 입장**
   - 모든 방은 고유 8자리 초대 코드가 자동 생성되며 코드를 통한 원클릭 입장 지원.
5. **참여 채팅방 전용 목록 조회**
   - 유저별 `chat_room_member` 매핑 기반으로 본인이 참여한 방만 빠르게 조회.
6. **Zero-Downtime WebSocket 배포 전략**
   - `server.shutdown=graceful` 적용으로 기존 연결 정리 시간 확보.
   - Client-side Auto-Reconnect (Exponential Backoff) 및 미수신 메시지 Sync Protocol 연동으로 배포 시 유저 끊김 최소화.
   - WS 노드의 완전 무상태화 (Kafka / Redis Pub-Sub fanout).

---

## 🏗️ 멀티 모듈 아키텍처

```
spring-chat/
├── chat-common/       # DTO, Enums (RoomType, MessageType), ErrorCode, Exception
├── chat-core/         # JPA Entities, Repositories, Business Services
├── chat-api/          # REST API Controllers (User, Room Management, Message History)
├── chat-ws/           # WebSocket / STOMP Gateway, Kafka Producer, Session Manager
├── chat-consumer/    # Kafka Consumer (PostgreSQL Bulk Persistence Worker)
└── chat-analytics/   # Apache Flink Stream Processing (Real-time CEP & Aggregation)
```

---

## 🚀 실행 가이드

### 1. 인프라 실행 (Docker Compose)
PostgreSQL, Redis, Apache Kafka, Zookeeper를 로컬에서 실행합니다:
```bash
docker-compose up -d
```

### 2. 애플리케이션 빌드 및 테스트
```bash
./gradlew build
./gradlew test
```

### 3. 모듈별 실행
- **REST API Server (8080 포트)**:
  ```bash
  ./gradlew :chat-api:bootRun
  ```
- **WebSocket Server (8081 포트)**:
  ```bash
  ./gradlew :chat-ws:bootRun
  ```
- **Kafka Consumer DB Worker (8082 포트)**:
  ```bash
  ./gradlew :chat-consumer:bootRun
  ```
- **Flink Analytics Stream Job**:
  ```bash
  ./gradlew :chat-analytics:run
  ```

---

## 🔌 API 명세 요약

| Method | Endpoint | 설명 |
| :--- | :--- | :--- |
| `POST` | `/api/users` | 회원 생성 (username, nickname) |
| `POST` | `/api/rooms` | 채팅방 개설 (`X-User-Id` 헤더 필요) |
| `POST` | `/api/rooms/join` | 초대 코드로 방 입장 |
| `GET` | `/api/rooms/my` | 내가 참여한 채팅방 목록 조회 |
| `GET` | `/api/rooms/{roomId}/messages` | 이전 메시지 내역 조회 |
| `GET` | `/api/rooms/{roomId}/sync` | 재연결 시 미수신 메시지 Gap 복구 |
| `WS` | `/ws-stomp` | STOMP 웹소켓 핸드셰이크 엔드포인트 |

---
