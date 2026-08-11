# FIRE — Frame Intelligence Rendering Engine

AI 영상 API를 단순 호출하는 앱이 아니라, 여러 장면을 안전하게 생성하고 실패를 복구하며 최종 영상으로 조립하는 제작 파이프라인입니다.

현재 베이스는 PostgreSQL에 작업과 입력 자산 메타데이터를 저장하고, 비용이 들지 않는 `MockVideoProvider`, 명시적으로 활성화하는 `GeminiVideoProvider`, 로컬 FFmpeg 렌더러로 다음 흐름을 실행합니다.

```text
프로젝트 생성 → 장면별 다중 이미지·영상 및 배경음악 업로드 → 타임라인 구성 → 비동기 MP4 렌더링
```

## 기술 구성

- Backend: Java 21, Spring Boot 4.1, Spring JDBC, Flyway, Maven
- Frontend: React 19, TypeScript, Vite
- Data: PostgreSQL 17
- Runtime: Docker Compose, Nginx
- Video generation: provider adapter pattern, Mock default, optional Gemini Omni Flash
- Input media: verified local file storage with SHA-256 metadata
- Deterministic rendering: FFmpeg fixed-argument pipeline, 720p/30fps MP4

## 빠른 실행

이 PC에는 Java 8만 설치되어 있으므로 전체 실행은 Docker를 권장합니다.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

PostgreSQL 데이터는 Compose의 `postgres-data` volume에 저장되므로 컨테이너를 다시 만들어도 프로젝트가 유지됩니다. `QUEUED` 또는 `PROCESSING` 상태에서 API가 재시작되면 완료되지 않은 장면부터 Mock 생성을 재개합니다.
업로드한 입력 미디어와 최종 MP4는 별도의 `media-data` volume에 저장됩니다. Docker API 이미지에는 FFmpeg와 FFprobe가 포함됩니다.

- Studio UI: <http://localhost:3000>
- Backend health: <http://localhost:8080/actuator/health>
- API projects: <http://localhost:8080/api/projects>

종료:

```powershell
docker compose down
```

### Gemini 영상 생성 활성화

기본값은 계속 `mock`이며 실제 과금 호출은 비활성화되어 있습니다. Google AI Studio의 Gemini API 키를 `.env`에 넣고 다음 두 스위치를 모두 켜야만 Studio의 공급자 목록에 `gemini`가 나타납니다.

```dotenv
FIRE_VIDEO_GEMINI_ENABLED=true
FIRE_VIDEO_GEMINI_PAID_GENERATION_ENABLED=true
GEMINI_API_KEY=your-key-here
```

그 뒤에도 사용자가 생성 화면에서 Gemini를 선택하고 장면별 유료 호출 동의 체크박스를 선택해야 요청이 접수됩니다. 키는 `x-goog-api-key` 헤더로만 전송되고 응답 원문이나 오류 메시지에 기록하지 않습니다.

현재 어댑터는 다음 입력을 지원합니다.

- 프롬프트만으로 영상 생성
- 한 장면의 JPEG/PNG/WebP 참조 이미지 최대 8개
- MP4 영상 1개와 편집 프롬프트(이미지와 영상 혼합은 아직 제외)
- `9:16` 또는 `16:9`, 비동기 제출·폴링, 결과 MP4 로컬 저장·브라우저 재생
- 생성된 장면 MP4를 장면 순서대로 우선 사용한 최종 FFmpeg 영상 조립

Gemini Omni Flash가 업로드 오디오 참조를 아직 지원하지 않으므로 프로젝트 배경음악은 Gemini 요청에 보내지 않고 최종 FFmpeg 렌더 단계에서 합성합니다. Gemini가 만든 장면이 있으면 최종 렌더러는 업로드 원본 대신 생성 장면을 우선 사용합니다. 생성 장면 자체의 네이티브 오디오는 장면 미리보기에서는 들을 수 있지만 현재 최종 렌더에서는 제외하고, 선택한 프로젝트 배경음악만 사용합니다. 모델·작업 ID·입력 해시·제출/완료 시각과 10초 기준 영상 출력 예상액을 장면에 기록합니다. 이 값에는 별도 입력 토큰 비용이 포함되지 않으며, 실제 가격이 바뀔 수 있으므로 `GEMINI_ESTIMATED_COST_PER_SECOND_USD`를 현재 계정 가격에 맞춰 조정해야 합니다.

### PostgreSQL 없이 로컬 백엔드 실행

백엔드를 직접 실행할 때는 별도 프로필이나 프로그램 인수 없이 H2 인메모리 DB를 사용합니다.
Compose 환경은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 전달해 PostgreSQL을 사용합니다.

```powershell
java -jar backend/target/fire-api-0.1.0-SNAPSHOT.jar
```

이 모드에서도 Flyway 마이그레이션과 Mock 영상 생성 흐름은 동작합니다. 최종 MP4 렌더링에는 로컬 PATH의 `ffmpeg`와 `ffprobe`가 필요합니다. 프로세스를 종료하면
프로젝트 데이터가 사라지므로 재시작 복구 검증에는 PostgreSQL을 사용해야 합니다.

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
  "renderPreset": "CINEMATIC",
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
POST /api/projects/{projectId}/generate?provider=gemini&confirmPaid=true
```

두 번째 요청은 서버의 Gemini 활성화 스위치와 사용자 유료 호출 확인을 모두 요구합니다. 완료된 Gemini 장면은 `GET /api/projects/{projectId}/scenes/{sceneId}/generated`에서 MP4로 조회합니다.

### 입력 미디어 업로드

프로젝트가 `DRAFT`일 때 장면별 이미지·영상 여러 개와 프로젝트 배경음악을 추가할 수 있습니다.
이미지·영상에는 해당 프로젝트의 `sceneId`가 필요하고, 배경음악에는 `sceneId`를 보내지 않습니다.

```powershell
curl.exe -X POST http://localhost:8080/api/projects/{projectId}/assets `
  -F "kind=IMAGE" `
  -F "sceneId={sceneId}" `
  -F "timelinePosition=0" `
  -F "durationMs=500" `
  -F "file=@C:\media\opening.png"

curl.exe -X POST http://localhost:8080/api/projects/{projectId}/assets `
  -F "kind=AUDIO" `
  -F "file=@C:\media\music.mp3"
```

- 이미지: PNG, JPEG, WebP, GIF, 최대 15MB
- 동영상: MP4/MOV, WebM, 최대 200MB
- 오디오: MP3, WAV, OGG, M4A, AAC, 최대 30MB
- 타임라인: 프로젝트당 이미지·동영상 합계 최대 300개, `timelinePosition` 순서로 재생
- 사진 속도: `durationMs=500`이면 초당 2장, UI에서 초당 0.5~5장 조절
- 동영상: `durationMs`를 생략하면 FFprobe로 확인한 원본 길이를 사용
- 내용 조회: `GET /api/projects/{projectId}/assets/{assetId}/content`

서버는 확장자나 브라우저의 MIME 선언만 신뢰하지 않고 파일 시그니처를 확인합니다. 원본 파일명은 저장 경로에 사용하지 않으며 SHA-256 해시와 함께 자산 메타데이터로 기록합니다.

### 최종 MP4 렌더링

```http
POST /api/projects/{projectId}/render
GET  /api/projects/{projectId}/render/content
```

렌더 작업은 비동기로 실행되고 프로젝트 조회 응답의 `render.status`가 `QUEUED → PROCESSING → COMPLETED/FAILED`로 바뀝니다. 사진과 동영상 사이에는 부드러운 전환을 넣고, 배경음악은 전체 영상 길이에 맞춰 반복한 뒤 종료합니다. 출력은 화면 비율에 맞춘 720p, 30fps H.264/AAC MP4입니다.

지원 프리셋은 `CLEAN`, `ROMANTIC`, `FAIRYTALE_PARK`, `CINEMATIC`입니다. 자유 스타일 프롬프트는 AI provider 입력으로 기록되고, 로컬 FFmpeg에는 프롬프트 문자열이 아니라 선택된 프리셋의 고정 필터만 전달됩니다.

### 상태 확인

```http
GET /api/projects/{projectId}
GET /api/projects
GET /api/providers
```

`MockVideoProvider`는 업로드 자산이 AI provider 계약까지 전달되는 흐름을 과금 없이 검증합니다. `GeminiVideoProvider`는 Google Interactions API에 백그라운드 작업을 제출하고 저장된 작업 ID로 결과를 재개합니다. 실제 파일 합성은 별도의 FFmpeg 렌더 작업이 담당합니다.

## 포트폴리오 목표

기능 개수보다 다음 값을 측정합니다.

- 작업 성공률과 장면별 재시도 횟수
- 서버 재시작 후 작업 복구율
- 전체 재생성 대비 부분 재생성 비용
- 예상 비용과 실제 비용 차이
- 영상 한 건의 평균 제작 시간
- 프롬프트·모델·seed·입력 자산 추적률

## 다음 구현 순서

1. 생성 시도 이력 테이블과 provider idempotency key 저장
2. Redis 또는 메시지 큐 기반 worker 분리
3. Gemini 실제 계정 스모크 테스트와 예상/실제 비용 대조
4. 장면 단위 재시도와 부분 재생성
5. 원본 동영상 음성과 배경음악 믹싱, 음성·자막 생성
6. 비용 승인과 사용량 대시보드
7. 콘텐츠 출처·동의·안전 정책 기록

자세한 설계는 [`docs/architecture.md`](docs/architecture.md)를 참고하세요.
