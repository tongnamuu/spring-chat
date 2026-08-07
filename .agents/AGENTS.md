# Project Rules (AGENTS.md)

- **테스트 환경 규칙**: 데이터베이스 테스트는 H2 등 인메모리 DB를 사용하지 않고, 항상 실제 PostgreSQL을 `Testcontainers`로 띄워서 검증을 진행한다.
- **Java 환경**: Java 25 (`25-tem`) SDKMAN 환경을 사용한다.
