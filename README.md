# FSS_Supporter

금융감독원 검사·감독 업무를 돕는 Windows 앱입니다. 내부망에서도 쓸 수 있도록 국가법령정보(www.law.go.kr)와 OpenDART에 직접 연결합니다.

| 메뉴 | 하는 일 |
| --- | --- |
| 대시보드 | 진행 중 작업, 기한 지남, 14일 일정, 관심기업 새 공시, 최근 법령 검색 |
| 작업 관리 | 검사·리포트·의견서 작업(상태·기한·체크리스트·관련 법령)과 일정 달력 |
| 검사 진행 관리 | 검사품질관리 매뉴얼 단계별 점검 항목·기한(착수일/종료일 기준), 단계별 관련 서식 바로 작성, 매뉴얼·서식 HWP 가져오기(원문은 PC에만 저장) |
| 스마트 법령검색 | 약칭·조문 검색, 법률→시행령→시행규칙→감독규정 위임 트리, 기준일 조회, HWPX 내보내기, 오프라인 저장본 |
| 기업 개황·공시 | 기업개황, 최대주주·지분, 재무, 공시 목록, 관심기업 새 공시 |
| 의견서 도우미 | 검사의견서·조치안·검사서 초안, 인용 조문 검증(현행/기준일), HWPX·DOCX |
| 제재 양정 계산기 | 과징금·과태료(위반 금액·한도 × 부과기준율, 가중·감경, 한도·끝수), 임원·직원·기관 양정 단계, 엑셀·조치안 넣기 |

## 설치

릴리스의 `FSS_Supporter-버전-x64-Setup.exe`(일반 PC) 또는 `-arm64-Setup.exe`(ARM PC)를 실행합니다. 관리자 권한 없이 `%LOCALAPPDATA%\Programs\FSS_Supporter`에 설치됩니다.

## 업데이트

- 인터넷 PC: 앱 정보에서 자동 확인(하루 1회) 후 설치합니다.
- 내부망 PC: 설치 파일, `latest.json`, `latest.json.sig`를 한 폴더에 옮긴 뒤 앱 정보 → '파일로 설치'.
- `latest.json`은 저장소 주인의 GitHub Secret `UPDATE_SIGNING_KEY`(Ed25519 개인키)로 서명합니다. 앱은 내장 공개키(`update/UpdateKey.kt`)로 서명과 파일 해시를 확인한 뒤에만 설치하므로, 다른 사람이 만든 빌드는 업데이트로 받아들여지지 않습니다.

## 빌드

```
./gradlew test
./gradlew :app:run
./gradlew :app:createDistributable   # 앱 이미지
```

설치 파일은 GitHub Actions(`build.yml`)가 x64·ARM64용으로 만들고, 배포는 저장소 주인이 `v*` 태그를 올릴 때 `release.yml`이 합니다.

만든이: 이현덕 with Claude (hdlee73@gmail.com, https://hdlee73.github.io/Home/)
