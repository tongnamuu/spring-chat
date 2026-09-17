# High-throughput room delivery

## 구현 요약

목표는 한 방에 1,500명, 초당 10,000건, 모든 원문을 1초 안에 수신하는 것입니다.
저장과 배포를 분리하고, 방 단위 순서를 유지하면서 각 단계의 작업을 묶습니다.
이 목표를 만족했다고 판단하려면 실제 1,500클라이언트 부하 검증이 필요합니다.

흐름: STOMP 입력 -> Kafka 원본 -> DB 배치 저장/커밋 -> Kafka 저장 완료 배치
-> 각 WS 서버의 로컬 구독자 -> 클라이언트 중복 검사/프레임별 화면 갱신.

- 원문 전송량은 사용자 수에 비례합니다. 500바이트 기준 서버 총 60Gbps,
  사용자 한 명당 40Mbps이며 프로토콜 오버헤드는 별도입니다.
- 저장은 최대 500건의 JDBC 배치, 전송은 최대 100건 및 예상 JSON 크기로
  제한합니다. `eventId` 중복 저장을 막고 Kafka 재처리 시 다시 배포합니다.
- 배치의 직전 메시지 ID로 누락을 감지하고, 500건씩 고정 상한 ID까지 복구합니다.
- 화면에는 최근 500건을 유지합니다. 이는 원문 수신을 생략하는 샘플링이 아닙니다.
- 서버는 순서 보장 큐에 들어가기 전에 세션별 대기 바이트를 계산합니다.
  1MB를 넘는 느린 세션은 종료하고 재접속 시 DB 이력을 복구합니다.
- 방 정원은 1,500명까지 허용하고, 동시 입장 시 방 잠금으로 인원 수 충돌을 방지합니다.
- 대량 입력은 `/pub/chat/messages`에 최대 100건 배열로 보내 인증 비용을 줄입니다.
  일반 입력 `/pub/chat/message`도 유지됩니다. `/user/queue/publish-results`의
  `accepted`는 Kafka 접수 결과이며 모든 사용자 수신 완료를 뜻하지 않습니다.

## Target and capacity

One room: 10,000 messages/second, 1,500 subscribers, every original message
received within one second. This is an acceptance target, not a measured claim.
Offline or bandwidth-limited clients recover from history; a deadline cannot be
guaranteed across disconnected networks.

For N subscribers and B serialized bytes/message, egress is approximately
10,000 * N * B bytes/second. At N=1,500 and B=500 this is 7.5 GB/s (60 Gbit/s),
plus protocol overhead, and 5 MB/s (40 Mbit/s) per client. Batching reduces
frames, not payload volume. Measure worst-client latency and arrival skew, not
only averages or p99, because the requirement says every connected client.

## Plan

1. Persist Kafka input in JDBC batches with eventId uniqueness. Commit DB before
   publishing durable batches, and commit input offsets only after downstream
   Kafka acknowledgement. Retries may repeat delivery but not database rows.
2. Key both Kafka paths by room. Keep one ordered writer per room partition.
   WS nodes have independent fanout groups and send room batches locally.
3. Bound sync pages, index room/message IDs and batch sender lookups. Freeze a
   recovery upper bound so a hot stream cannot keep catch-up running forever.
   Hold live arrivals during recovery; overflow must trigger recovery again.
4. Receive all messages, but render a bounded recent window once per animation
   frame. Bound duplicate tracking and render queues. Disable frame logging.
5. Raise room capacity to 1,500. Test batching, retries, multi-node order,
   multi-page reconnect and UI bounds. Provide an opt-in load test reporting
   offered/delivered rates, missing IDs, latency and cross-client arrival skew.

## Deployment and acceptance

- Production Kafka: at least three brokers, replication factor 3,
  min.insync.replicas=2, acks=all and idempotence. Local Compose is not HA.
- Local Java processes default to `-Xms64m -Xmx256m` to avoid each process sizing
  its heap against the entire shared Docker VM. Override `CHAT_JAVA_TOOL_OPTIONS`
  for a provisioned environment; this local memory budget is not a production
  recommendation for 1,500 sockets. Account for native memory and other services.
- Do not increase ordered topic partitions in-place: drain and cut over to new
  topics. One hot room cannot scale its sequencer by adding consumers.
- Enable PostgreSQL reWriteBatchedInserts, provision WAL/disk throughput and
  backups. 10,000/s is 864 million rows/day: retention, table partitioning and
  archiving must be provisioned before sustained production use.
- The persisted Kafka topic and WS batch payload change the protocol. Coordinate
  deployment and reload old clients. Raw events remain replayable in Kafka.
- Bound send times, session buffers and channel queues. A client whose sustained
  bandwidth is below the stream rate cannot meet the deadline or catch up.
- Size WS nodes by bytes/s and sockets. At larger scale use room-aware routing
  instead of all nodes consuming all rooms. The simple broker is not evidence
  of support for arbitrary subscriber counts.
- Final acceptance: distributed 1,500-client test at 10,000/s for >=10 minutes,
  representative payload sizes, zero missing unique IDs, identical order,
  bounded heap, and maximum connected-client delivery latency <=1 second.
  Include slow clients and restart WS/consumer nodes; separately measure replay
  recovery. Record Kafka lag, DB latency, network saturation and client stalls.

References:
- https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html
- https://docs.spring.io/spring-framework/reference/web/websocket/stomp/ordered-messages.html
- https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html

## Load test

Run from `e2e` after `npm ci` and after the applications and Kafka consumers are ready:

```sh
CLIENTS=5 RATE=1000 SECONDS=3 npm run load:room
CLIENTS=50 RATE=10000 SECONDS=10 npm run load:room
CLIENTS=1500 RATE=10000 SECONDS=600 MESSAGE_BYTES=256 npm run load:room
```

This creates real accounts, a room and persisted messages. It never deletes
existing data. `MESSAGE_BYTES` is ASCII filler size; IDs, timestamps and JSON
increase the actual wire size. Each client tracks all expected sequence numbers
in a bitset; long 1,500-client runs need substantial generator memory and CPU.

For multiple generators, supply the same `RUN_ID`, `ROOM_ID`, `INVITE_CODE`,
`RATE`, `SECONDS` and future epoch-millisecond `START_AT` to every process.
Only one generator has `PUBLISH=true`; other shards use `PUBLISH=false` and
their own `CLIENTS` count. Create the shared room with a test account first.
Account setup must finish before START_AT. Synchronize machine clocks and merge
the shard reports; the script's arrival-skew measurement is local to each shard.

The exit code is nonzero on missing messages, order inversion, disconnect,
protocol failure, maximum delivery latency above 1,000ms, or a generator that
fails to offer at least 98% of the requested rate. This socket test does not
measure browser rendering; the Playwright tests cover the bounded UI separately.

## 검증 결과와 남은 작업

2026-09-17 로컬 측정 원본 요약은 `high-throughput-results-2026-09-17.json`에 있습니다.
기능 회귀 테스트는 통과했지만 **1,500명/초당 1만 건/최대 지연 1초 목표는 미달성**입니다.
1,500클라이언트 전체 시험은 실행하지 않았습니다.

| 조건 | 결과 |
| --- | --- |
| 5명, 초당 1,000건, 3초 | 15,000회 모두 수신, 최대 55ms |
| 50명, 초당 10,000건, 5초, JVM 힙 상한 적용 | 2,500,000회 모두 수신, 중복/순서 역전 0, 최대 17,623ms |
| 위 50명 시험의 클라이언트 간 최대 도착 차이 | 229ms |

50명 시험은 송신 종료 후 30초의 대기 구간을 포함하면 모두 도착했지만,
입력 처리까지 최대 17,380ms가 걸렸습니다. 거의 같은 시각에 도착한다는 것과
발신 후 1초 이내에 도착한다는 것은 다른 조건입니다. 이 구현을 목표 성능이
검증된 운영 버전으로 취급하면 안 됩니다.

다음 단계는 다음 순서로 진행합니다.

1. 다른 워크로드와 분리된 서버와 여러 부하 생성기에서 같은 시험을 실행합니다.
   생성기 이벤트 루프 지연, 네트워크 대역폭, JVM GC와 단계별 큐 길이를 함께 측정합니다.
2. 입력 경로를 우선 프로파일링합니다. 현재 프레임별 DB 활성 사용자 검사와 Redis
   세션 갱신, 연결별 순서 처리 비용이 있습니다. 탈퇴 즉시 차단 동작을 보존하면서
   인증/세션 확인 비용을 줄이고, 여러 발신자의 부하도 따로 측정해야 합니다.
3. 전송 측 메모리 상한 외에 입력 측 순서 큐의 명시적인 바이트 admission limit과
   송신자의 미확인 요청 수 제한을 추가해야 합니다. executor queueCapacity만으로는
   Spring의 연결별 순서 큐까지 제한되지 않습니다. 과부하를 무한 대기로 숨기면 안 됩니다.
4. 노드당 실제 전송량을 측정해 WS 수와 네트워크를 산정하고, Kafka 복제/DB 보관
   정책을 적용합니다. 현재 Compose의 단일 브로커는 장애 내성을 검증하는 구성이 아닙니다.
5. 1,500명/초당 1만 건의 10분 시험, 느린 수신자, 재접속, WS 및 consumer 재시작
   시험을 통과해야 운영 수용 조건을 완료합니다.
