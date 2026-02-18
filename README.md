# 업무 일정 관리 앱 (모노레포)

프로덕트 배포를 고려한 업무 일정 관리 앱입니다. 로그인 강제, 자동로그인, 트리 기반 항목, 블록 편집, 이미지 업로드, 백업/복원, ZIP 마이그레이션, CI/관측 구성을 포함합니다.

## 1. 모노레포 구조
- `/Users/eomtaemin/Desktop/Develop/Codex/schedule-workspace-pro/backend`: Spring Boot API
- `/Users/eomtaemin/Desktop/Develop/Codex/schedule-workspace-pro/frontend`: React SPA
- `/Users/eomtaemin/Desktop/Develop/Codex/schedule-workspace-pro/infra`: Docker Compose, Nginx, Jenkins, Scouter
- `/Users/eomtaemin/Desktop/Develop/Codex/schedule-workspace-pro/docs`: 운영/보안/배포/마이그레이션 문서

## 2. 빠른 실행
### 로컬
1. Backend
```bash
cd backend
gradle bootRun
```
2. Frontend
```bash
cd frontend
npm install
npm run dev
```

### Docker Compose (필수 명령)
- 최소(dev):
```bash
cd infra
docker compose --profile dev up -d --build
```
- 전체(full):
```bash
cd infra
docker compose --profile full up -d --build
```

## 3. 환경 변수
`.env.example`를 참고해 `.env`를 작성합니다.

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`: DB 연결
- `CORS_ALLOWED_ORIGINS`: 허용 Origin 화이트리스트
- `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`: JWT 시크릿
- `ADMIN_SEED_ENABLED`, `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD`: 초기 관리자 계정 시드

비밀값은 절대 커밋하지 않습니다.

## 4. 핵심 기능
- 인증: 회원가입/로그인/자동로그인(Refresh Cookie + CSRF 헤더)/로그아웃
- 권한: USER/ADMIN 역할 분리 + 관리자 전용 API/대시보드
- 워크스페이스: 트리 항목 생성/수정/삭제/검색/최근 항목
- 콘텐츠: 블록 저장/조회 + 자동 저장(디바운스)
- 속성 바: 상태/날짜/수정일 표시
- 파일: 이미지 업로드/렌더 (MIME/확장자/경로/용량 제한)
- 백업/복원: `/api/backup/export`, `/api/backup/import`
- 마이그레이션: `/api/migration/import` (Detect/Parse/Map/Persist + 실패 리포트)
- UI: 헤더/푸터/회원가입 화면/관리자 대시보드(`/admin`)

## 5. 보안 체크리스트
- CORS 환경 분리/화이트리스트
- Refresh 토큰 쿠키 + CSRF 토큰 검증
- bcrypt 비밀번호 해시
- 로그인/재발급 Redis 레이트 제한
- 보안 헤더: CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- XSS 방지: 서버 HTML 주입 금지, 프론트 위험 API 미사용

## 6. 테스트
- Backend: `gradle test` (Testcontainers Postgres/Redis)
- Frontend: `npm run test`
- E2E: `npm run e2e`

## 7. CI/CD
- Jenkinsfile: `/Users/eomtaemin/Desktop/Develop/Codex/schedule-workspace-pro/infra/jenkins/Jenkinsfile`
- 순서: backend test/build -> frontend test/build -> docker build -> smoke test

## 8. 관리자 접속
- 기본 관리자 계정(개발 기본값):
  - 이메일: `admin@example.com`
  - 비밀번호: `Admin1234!`
- 로그인 후 ADMIN 권한이면 상단 메뉴의 `관리자 대시보드` 접근 가능

## 9. 운영 체크리스트
- HTTPS 인증서 적용(리버스 프록시)
- 운영 CORS 도메인 제한
- 포트 노출 최소화
- JWT 시크릿 교체
- 업로드 볼륨 백업 정책 수립
- Scouter 수집 정상 여부 확인

## 10. 참고한 공식 문서 (버전/확인일)
확인일: 2026-02-18

- Spring Boot Docs (적용: 3.3.5): https://docs.spring.io/spring-boot/index.html
- Spring Security Docs (적용: Boot BOM 기준): https://docs.spring.io/spring-security/reference/
- Springdoc OpenAPI (적용: 2.6.0): https://springdoc.org/
- PostgreSQL Docs (적용 컨테이너: 16): https://www.postgresql.org/docs/
- Redis Docs (적용 컨테이너: 7): https://redis.io/docs/latest/
- Docker Compose Docs (Compose V2): https://docs.docker.com/compose/
- Jenkins Docs (LTS): https://www.jenkins.io/doc/
- Scouter GitHub: https://github.com/scouter-project/scouter
- React Docs (공식 문서 기준 확인): https://react.dev/
- Vite Docs (적용: 5.4.9): https://vite.dev/
- Tiptap Docs (적용: 2.8.0): https://tiptap.dev/docs
- shadcn/ui Docs: https://ui.shadcn.com/docs
- JWT RFC 7519: https://www.rfc-editor.org/rfc/rfc7519
