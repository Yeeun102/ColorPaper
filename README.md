# ColorPaper 🎨

대학생의 기록을 위한 컬러풀한 다이어리 서비스, **ColorPaper**
가족, 친구와 함께 소중한 일상을 기록하고 공유하세요.

## 🚀 주요 기능
- **다이어리 기록:** 날짜별 메모와 포스트잇 스타일의 일기 작성
- **단어장:** 나만의 단어장 추가 및 학습 기능
- **프로필 & 친구:** 친구 프로필 확인 및 다이어리 공유
- **리마인드:** 잊지 않도록 도와주는 알림 서비스

## 🛠️ 기술 스택
- **Language:** Kotlin
- **Architecture:** MVVM
- **UI:** Android View (XML)
- **Networking:** Retrofit2
- **Image Loading:** Coil

## 📂 프로젝트 구조
프로젝트는 기능별로 패키지가 분리되어 있습니다.
```text
ui/                 # View Layer: 화면 UI 및 이벤트 처리
├── login/          # 로그인 및 회원가입 관련 화면
├── home/           # 메인 대시보드 및 네비게이션
├── diary/          # 일기 작성, 수정, 상세 조회
├── flashcard/      # 단어장 기능 및 학습 모듈
└── profile/        # 사용자 프로필 및 친구 관리
data/               # Data Layer: 서버 통신 및 로컬 저장소
├── remote/         # Retrofit API 인터페이스 및 DTO
└── local/          # Room DB, SharedPreference 등 로컬 저장소
model/              # Domain Layer: 데이터 모델(Entity) 정의
└── util/               # 공통 유틸리티 (확장 함수, 커스텀 뷰, 상수 등)
