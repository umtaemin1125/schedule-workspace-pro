# 마이그레이션 가이드

## API
- `POST /api/migration/import`
- `multipart/form-data`로 ZIP 업로드

## 파이프라인
1. Detect: ZIP 내 csv/md/html/image 탐지 (중첩 ZIP 포함)
2. Parse: CSV 표/문서 파싱
3. Map: 트리/속성/블록 매핑
4. Persist: DB 저장 + 파일 저장 + 문서 내 이미지 경로를 `/files/{storedName}`로 자동 치환

## 실패 리포트
응답에 아래 항목 포함:
- `detectedPatterns`
- `persistedItems`
- `persistedFiles`
- `failures`
- `manualFixHints`

## 운영 권장
- 먼저 `/api/backup/export`로 백업
- 스테이징 환경에서 import 검증 후 운영 반영
