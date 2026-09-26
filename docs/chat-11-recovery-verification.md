# CHAT-11 복구 구현 및 검증

검증일: 2026-09-26, 브랜치: `codex/CHAT-11-tail-message-sync`.

## 동작

- 서버가 구독 등록을 끝낸 뒤 해당 세션·구독에 준비 알림을 보낸다.
- 준비 알림 이후 기록을 조회하고, 복구가 끝나면 입력과 전송을 활성화한다.
- 연결 중 마지막 메시지만 누락되는 경우에도 5~6초마다 최신 메시지 ID를 확인해 복구한다. 정상 확인 동안 입력 상태는 바뀌지 않는다.
- 누락된 기록은 상한 ID를 고정해 500개씩 조회하며, 실시간 수신과 겹친 메시지는 중복 제거한다.
- 오프라인·재연결 중 전송을 차단하고 작성 중 초안과 수신 커서를 보존한다.
- 방 변경·로그아웃·연결 변경 이전 요청 응답은 무시하고, 실패한 복구는 재시도한다.
- 하트비트 스케줄러는 Spring 관리 빈을 주입한다. Nginx는 Host의 포트를 보존한다.

## 검증 결과

- `./gradlew build`: 성공. PostgreSQL Testcontainers 사용. Java 테스트 52개, 실패/오류/건너뜀 0개.
- `./gradlew :chat-api:bootJar :chat-ws:test --rerun-tasks`: 성공. 최신 웹 파일 빌드 및 WS 테스트 재실행.
- `node --test e2e/load/tail-sync.test.cjs`: 12개 통과.
- `BASE_URL=http://localhost:18080 node --test e2e/load/session-lifecycle.test.cjs`: 로그아웃·탈퇴 2개 통과.
- `E2E_BASE_URL=http://localhost:18080 npx playwright test --workers=1` (e2e 디렉터리): 7개 중 6개 통과, 로그인 1개는 10초 연결 대기 시간 초과.
- `E2E_BASE_URL=http://localhost:18080 npx playwright test chat-1-login.spec.js --workers=1`: 실패 항목 단독 재검증 통과. 따라서 7개 시나리오 각각의 통과를 확인했으나, 마지막 전체 실행이 한 번에 모두 통과한 것은 아니다.
- `git diff --check`: 통과.

브라우저 검증은 실제 로컬 PostgreSQL, Kafka, Redis, API, consumer 및 여러 WS 노드를 사용했다. 구독 준비 차단과 대량 DOM 제한 테스트는 브라우저 안에서 수신/HTTP 응답을 제어했다. 마지막 메시지 누락 테스트는 실제 WS 배치 하나를 브라우저에서 고의로 버려 복구를 확인했다.

### 요구사항별 증거

| 요구사항 | 검증 |
|---|---|
| 마지막 메시지 누락 동기화 | 실제 연결을 유지한 채 마지막 수신 배치 폐기 후 화면에 정확히 1회 표시 |
| 구독 준비 | 서버 인터셉터 테스트, 준비 알림 전 기록 요청 0회 및 전송 비활성화 웹 테스트 |
| 기록 복구 완료 후 전송 | 기록 응답 지연 동안 전송 비활성화, 완료 후 활성화 |
| 네트워크 재연결 복구 | Playwright offline/online 전환, 누락 메시지 복구 및 초안 보존 |
| 대량 누락 및 중복 제거 | 1,200개 메시지 복구 순서와 실제 반영 횟수 검증, DOM 500개 제한 |
| 세션 종료 | 두 WS 노드의 로그아웃·탈퇴 연결 종료 |

## 실행 환경 및 반영 상태

80번 포트는 다른 서비스가 사용 중이어서 `/tmp/chat-11-compose-test.yaml`로 Nginx를 `127.0.0.1:18080`에 매핑했다. 테스트 서버는 `http://localhost:18080/`에서 실행 중이다. 이 포트에서 발생한 WS 403은 Nginx의 Host 포트 제거가 원인이었고, Host 보존 수정 후 실제 WS 테스트로 검증했다.

Jira 인증 환경변수가 없어 티켓 원문은 조회하지 못했다. 사용자가 명시한 세 가지 복구 동작과 웹 테스트를 기준으로 검증했다. Jira 상태는 변경하지 않았다.

이 기록은 PR 반영 전 로컬 검증 결과다. main 병합은 별도 절차로 진행한다. 기존 `docs/chat-room-5000mps-plan.md`는 이 작업의 완료 범위에 포함하지 않는다.
