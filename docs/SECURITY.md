# 보안 가이드

## 인증
- Access Token 짧은 만료
- Refresh Token 쿠키(httpOnly, sameSite)
- CSRF: `X-CSRF-TOKEN` 헤더와 쿠키 비교

## 입력/출력 보호
- 업로드 MIME/확장자/경로 검증
- 서버에서 원시 HTML 렌더 금지
- 프론트에서 위험한 직접 HTML 삽입 금지

## 네트워크
- CORS 화이트리스트 운영 분리
- reverse-proxy 보안 헤더 강제
- 관리자 포트 접근 제한

## 운영
- JWT 시크릿 주기적 교체
- 취약점 점검: `npm audit`, `gradle dependencyCheckAnalyze`(플러그인 사용 시)
