# Pleos Connect 개발 문서 로컬 사본

2026-11-07 기준 [Pleos 공식 개발 문서](https://document.pleos.ai/)의 한국어 페이지 99개를 검색용 텍스트로 저장했습니다. 각 파일 첫 줄에 원문 주소가 있습니다. 공식 문서의 저작권은 해당 권리자에게 있습니다.

- `docs/pleos-only/getting-started.txt`: 개발 시작 및 환경 설정
- `docs/pleos-only/connect-sdk-pleos/`: Vehicle, NaviHelper, 음성 등 SDK 사용 가이드
- `docs/pleos-only/vehicle-app-planning-guide/`: 차량 앱 기획과 플랫폼 제약
- `docs/pleos-only/vehicle-app-developer-guide/`: 차량 앱 개발 가이드
- `docs/pleos-only/app-test-guide/`: 에뮬레이터와 테스트
- `docs/pleos-only/distribute/`: 앱 등록과 심사

저장소 루트에서 `rg '검색어' docs/pleos`로 내용을 검색할 수 있습니다. 갱신하려면 `python3 scripts/fetch-pleos-docs.py`를 실행하세요. 인터넷 연결이 필요합니다.

이 사본은 기획 및 개발에 필요한 가이드 본문을 텍스트로 추출한 것입니다. 이미지, 링크 대상, 세부 API 클래스·메서드별 자동 생성 페이지는 포함하지 않으며 표와 코드의 서식이 원문과 다를 수 있습니다. 상세 API, 정확한 코드와 최신 버전은 각 파일에 적힌 원문을 확인하세요. 사이트맵에 있으나 페이지를 찾을 수 없는 `infolink-emulator/setup-infolink-emulator`는 제외했습니다. SDK와 에뮬레이터 설치 파일은 포함되지 않습니다.
