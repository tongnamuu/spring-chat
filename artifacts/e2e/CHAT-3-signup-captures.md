# CHAT-3 회원가입 화면 검증

검증 환경: `http://localhost/`, Chrome headless, Java 25 Docker Compose 스택

## 데스크톱 (1280x720)

| 캡처 | 검증 상태 | 확인 내용 |
| --- | --- | --- |
| [기본 회원가입 폼](./chat-3-signup-form-desktop.png) | 회원가입 탭 진입 | 로그인/회원가입 분리, 이메일 전용 가입, nickname/password/확인 필드 안내 |
| [서버 검증 오류 상단](./chat-3-signup-validation-desktop-top.png) | 잘못된 email, nickname, password 제출 | 각 입력 필드 아래에 서버 검증 사유 표시, 모달 상단과 탭 유지 |
| [서버 검증 오류 하단](./chat-3-signup-validation-desktop-bottom.png) | 오류가 있는 긴 폼의 하단 스크롤 | 비밀번호 오류, 확인 필드, 제출 버튼이 모달 안에서 겹치지 않음 |
| [비밀번호 확인 불일치](./chat-3-signup-password-mismatch-desktop.png) | password와 confirmation 불일치 | API 호출 전에 확인 필드 바로 아래에 오류 표시 |
| [중복 email](./chat-3-signup-duplicate-desktop.png) | 이미 생성된 email 재사용 | HTTP 409 응답을 email 필드의 사용자용 오류로 표시 |
| [회원가입 성공](./chat-3-signup-success-desktop.png) | 이메일 아이디 계정 생성 | 로그인 탭 자동 이동, 성공 배너, 생성한 이메일 자동 입력, password 미입력 상태 |

## 모바일 (390x844)

| 캡처 | 검증 상태 | 확인 내용 |
| --- | --- | --- |
| [모바일 필드 검증](./chat-3-signup-validation-mobile.png) | email/nickname/password 동시 오류 | 모달이 viewport 안에 유지되고 텍스트, 입력, 오류, 버튼이 겹치거나 잘리지 않음 |

## 자동 검증

- `./gradlew test`: 전체 Java 테스트 통과
- `npm test`: CHAT-1 로그인, CHAT-2 탈퇴, CHAT-3 회원가입 브라우저 시나리오 통과
- `npm audit --audit-level=high`: 취약점 0건
- PostgreSQL Testcontainers: 성공 201, 중복 409, 필드별 검증 400, BCrypt 해시 저장 검증
- CapturedOutput: 회원가입 응답과 애플리케이션 로그에 원문 비밀번호 및 저장 해시가 포함되지 않음을 검증
