# Scouter Agent 준비

`backend` 컨테이너는 `/opt/scouter/agent/scouter.agent.jar`를 참조합니다.

1. 공식 저장소 릴리즈에서 `scouter.agent.jar` 다운로드
2. 이 폴더(`infra/scouter`)에 파일 복사
3. `docker compose --profile full up -d --build`

서버 포트:
- Collector: 6100
- UI/Web: 6180
