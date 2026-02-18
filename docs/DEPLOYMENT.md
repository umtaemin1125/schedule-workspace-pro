# 배포 가이드

## 기본 원칙
- `docker compose --profile full up -d --build`로 전체 구성 기동
- 리버스 프록시에서 HTTPS 종단 처리
- backend는 내부망 통신만 허용

## 서비스 포트
- reverse-proxy: 80/443
- frontend: 8081 (dev)
- backend: 8080
- postgres: 5432
- redis: 6379
- jenkins: 8088
- scouter-server: 6100, 6180

## 배포 절차
1. `.env` 주입
2. 이미지 빌드 및 기동
3. 헬스체크 확인
4. Jenkins 파이프라인 실행
5. Scouter 모니터링 확인
