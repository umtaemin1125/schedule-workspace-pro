# 운영 가이드

## 헬스체크
- backend: `/actuator/health`
- frontend: `/health`
- postgres: `pg_isready`
- redis: `redis-cli ping`

## 로그
- backend: 컨테이너 로그 + actuator metrics
- reverse-proxy: 접근/에러 로그

## 복구
1. 백업 ZIP 확보
2. 신규 환경 기동
3. `/api/backup/import` 복원
4. 핵심 데이터 샘플 검증
