# Changelog

이 프로젝트의 모든 주요 변경 사항을 기록한다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 버전은 [유의적 버전(SemVer)](https://semver.org/lang/ko/)을 준수한다.

## [Unreleased]

### 변경
- 문서 체계 재편: Living 문서(`docs/*`) + 캡스톤 동결 아카이브(`docs/capstone-1.0/`) 분리, 중복 산출물 정리
- 저장소 GitHub 단일화(Public), 1인 trunk-based 개발 모델로 전환

---

## [1.0.0] - 2026-06-25 — 캡스톤 제출본

SSAFY 11기 자율 프로젝트 팀(전진·송정기) 최종 제출본. 태그 `v1.0-capstone`.

### 추가
- **회원·인증**: 회원가입/로그인, JWT(HttpOnly 쿠키) 인증, 카카오 소셜 로그인, 마이페이지
- **관광지**: 한국관광공사 TourAPI 기반 전국 관광지 DB 수집, 지역·카테고리별 조회, 상세 정보
- **여행 일정**: 후보 장소 등록, 드래그앤드롭 일정 확정, Naver Maps 연동
- **이동시간 자동 계산**: ODsay·T Map 연동, 대중교통 구간·도보 경로 지도 시각화, 다층 캐싱
- **실시간 협업**: STOMP 기반 동시 일정 편집, 낙관적 락 + grab 게이트 동시성 제어
- **커뮤니티**: 여행 일정 공유 게시판, 댓글·좋아요, 공지사항
- **AI 챗봇**: Spring AI 기반 관광지 주변 추천
- **프론트엔드**: Vue 3 + Vite 마이그레이션 및 전 도메인 API 연동
- **배포**: Docker Compose(nginx + backend + MySQL) 단일 호스트 배포

[Unreleased]: https://github.com/DreamingGenie/TripCraft/compare/v1.0-capstone...HEAD
[1.0.0]: https://github.com/DreamingGenie/TripCraft/releases/tag/v1.0-capstone
