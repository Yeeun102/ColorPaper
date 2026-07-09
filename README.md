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
ui/
├── login/          # 로그인, 회원가입
├── home/           # 홈 화면 (체크리스트, 투두, n년전 등)
├── main/           # 하단바를 포함한 메인 컨테이너
├── diary/          # 캘린더, 메모(개별날짜), 셀프댓글 등
├── flashcard/      # 암기카드 생성/학습
├── profile/        # 내 프로필, 수정, 하이라이트 등
├── friend/         # 친구 검색, 친구 다이어리, 반응(좋아요/댓글)
└── setting/        # 설정(개인정보, 반복주기, 태그, 테마 등)
data/
├── model/          # DTO, 엔티티 
├── remote/         # API
└── local/          # DB
util/
