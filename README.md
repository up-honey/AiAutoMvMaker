# FIRE — Frame Intelligence Rendering Engine

AI 영상 API를 단순 호출하는 앱이 아니라, 여러 장면을 안전하게 생성하고 실패를 복구하며 최종 영상으로 조립하는 제작 파이프라인입니다.

현재 베이스는 PostgreSQL에 작업을 저장하고, 비용이 들지 않는 `MockVideoProvider`로 다음 흐름을 실행합니다.

```text
프로젝트 생성 → 장면 큐 등록 → 비동기 생성 → 진행 상태 갱신 → 완료 결과 확인
```

## 기술 구성

- Backend: Java 21, Spring Boot 4.1, Spring JDBC, Flyway, Maven
- Frontend: React 19, TypeScript, Vite
- Data: PostgreSQL 17
- Runtime: Docker Compose, Nginx
- Video generation: provider adapter pattern, mock provider first
- Planned rendering: FFmpeg

## 빠른 실행

이 PC에는 Java 8만 설치되어 있으므로 전체 실행은 Docker를 권장합니다.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

PostgreSQL 데이터는 Compose의 `postgres-data` volume에 저장되므로 컨테이너를 다시 만들어도 프로젝트가 유지됩니다. `QUEUED` 또는 `PROCESSING` 상태에서 API가 재시작되면 완료되지 않은 장면부터 Mock 생성을 재개합니다.

- Studio UI: <http://localhost:3000>
- Backend health: <http://localhost:8080/actuator/health>
- API projects: <http://localhost:8080/api/projects>

종료:

```powershell
docker compose down
```

프런트엔드만 검증:

```powershell
npm.cmd --prefix frontend install
npm.cmd --prefix frontend run build
```

백엔드만 검증:

```powershell
docker build -t fire-api-dev ./backend
```

## API

### 프로젝트 생성

```http
POST /api/projects
Content-Type: application/json

{
  "title": "퇴근 후 30초 여행",
  "topic": "서울의 밤 산책",
  "stylePrompt": "cinematic, warm neon, realistic",
  "aspectRatio": "9:16",
  "scenePrompts": [
    "비 온 뒤 네온이 반사되는 골목",
    "한강을 따라 천천히 이동하는 카메라",
    "도시 야경 위로 나타나는 엔딩 타이틀"
  ]
}
```

### 생성 시작

```http
POST /api/projects/{projectId}/generate?provider=mock
```

### 상태 확인

```http
GET /api/projects/{projectId}
GET /api/projects
GET /api/providers
```

## 포트폴리오 목표

기능 개수보다 다음 값을 측정합니다.

- 작업 성공률과 장면별 재시도 횟수
- 서버 재시작 후 작업 복구율
- 전체 재생성 대비 부분 재생성 비용
- 예상 비용과 실제 비용 차이
- 영상 한 건의 평균 제작 시간
- 프롬프트·모델·seed·입력 자산 추적률

## 다음 구현 순서

1. 생성 시도 이력과 idempotency key 저장
2. Redis 또는 메시지 큐 기반 worker 분리
3. Sora 또는 Veo provider 하나 연결
4. 장면 단위 재시도와 부분 재생성
5. 음성·자막·FFmpeg 렌더링
6. 비용 승인과 사용량 대시보드
7. 콘텐츠 출처·동의·안전 정책 기록

자세한 설계는 [`docs/architecture.md`](docs/architecture.md)를 참고하세요.
