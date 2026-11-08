# Pleos 개발 환경

이 프로젝트는 Pleos Connect 차량용 앱이다. 개발 스택은 **Kotlin과 Jetpack Compose**다. 현재는 Hello World 단계이며 Pleos SDK는 아직 연동하지 않았다.

## 작업 방식

- 모든 일반 작업은 GitHub 이슈를 먼저 만들고, 이슈별 브랜치에서 작업한 후 PR로 `main`에 반영한다. 긴급하거나 사용자가 다른 절차를 명시한 경우 그 지시를 따른다.
- 이슈의 완료 조건을 바탕으로 기능 구현 전에 `Given / When / Then` 형식의 Gherkin 시나리오를 작성한다. 정상 흐름과 필요한 오류·미지원 상황을 포함한다. 시나리오를 기준으로 구현하고 Cucumber 단계와 테스트를 작성한다. Gherkin 파일만 추가하면 테스트가 자동 완성되는 것은 아니므로 실행 결과를 확인한다.
- `.feature` 파일은 기능별로 관리하고, `Feature` 설명의 `Issues:` 아래에 해당 기능을 정의하거나 변경한 이슈의 제목과 링크를 한 번씩 기록한다. 시나리오 본문에는 현재 요구 동작을 유지하고 이슈 자동 종료는 PR의 `Closes #번호`를 사용한다.
- Gherkin 파일의 위치, 이슈 헤더와 시나리오 예시는 [Gherkin 작성 참고](docs/gherkin-guide.md)를 따른다. Cucumber 단계는 `app/src/androidTest/java/kr/roadcanvas/app/test/`에서 연결한다.
- 이슈를 만들기 전에 기존 이슈를 검색해 중복을 피한다. 제목은 구체적인 작업이나 문제를 한 줄로 적는다. 이슈 템플릿의 **배경**과 체크 가능한 **완료 조건**을 채우고 필요한 경우 작업 범위, 재현 방법과 참고 자료를 덧붙인다. 범위가 크면 이슈를 나눈다. 사용 가능한 라벨과 담당자가 있으면 지정하고, 없으면 억지로 만들지 않는다.
- `main`을 최신 상태로 확인한 뒤 `feat/123-short-description`, `fix/123-short-description`, `chore/123-short-description`처럼 이슈 번호와 영문 소문자·하이픈을 사용해 브랜치를 만든다. **한 이슈당 브랜치 하나와 PR 하나**를 원칙으로 한다.
- PR 제목은 커밋 제목과 같은 형식으로 작성한다. PR 템플릿의 **변경 사항**과 **검증**에 변경 이유, 검증 결과와 미확인 사항을 적는다. 화면이나 동작이 바뀐 경우 에뮬레이터 스크린샷을 첨부하고, 첨부하지 못했다면 이유를 적는다. GIF와 영상은 사용하지 않는다. `Closes #123`으로 이슈를 연결한다. 관련 없는 변경은 섞지 않는다.
- 혼자 작업하므로 사람의 리뷰 승인은 요구하지 않는다. PR-Agent 리뷰가 생성되면 내용을 확인하고 타당한 지적을 수정한 뒤 다시 검증한다. PR의 변경 사항과 검증 결과를 직접 확인하고 필수 검증 통과 후 squash merge한다. 브랜치 보호나 필수 체크는 저장소 설정에서 별도 활성화해야 한다.
- README는 제출용이다. 사용자의 별도 지침이 없는 한 README 파일을 작성하거나 수정하지 않는다. 개발 절차와 환경 정보는 이 파일에 기록한다.

## 커밋 컨벤션

- 제목은 `type: 설명` 형식이다. 허용 타입: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`. 제목은 72자 이내이며 설명은 한국어를 기본으로 구체적으로 적는다. 고유명사나 기술 용어는 영문을 사용할 수 있다. 예: `feat: 주행 상태 표시 추가`.
- 이슈 번호는 커밋 제목에 강제하지 않는다. PR 본문의 `Closes #번호`로 연결한다. 의미 있는 단위로 커밋하고 비밀 정보나 생성된 빌드 산출물을 포함하지 않는다.
- 이슈·커밋·PR의 제목과 본문에 이모지, `Generated with Claude Code`, LLM 도구가 붙이는 `Co-Authored-By` 같은 자동 서명이나 출처 표기를 넣지 않는다. 모델명, 추론 강도(effort), 사용한 에이전트 등 작업 도구 정보도 적지 않는다. 변경 내용과 검증 결과에만 집중한다.
- 최초 한 번 `git config --local core.hooksPath .githooks`를 실행해 저장소 훅을 활성화한다. Python 3이 필요하다. `commit-msg` 훅이 제목 형식과 메시지의 일부 이모지·자동 서명을 검사한다. 모든 표현을 자동으로 잡을 수는 없으므로 작성 시 직접 확인한다. 훅은 복제 시 자동 활성화되지 않으며, CI는 PR 제목도 같은 규칙으로 검사한다. merge·revert·fixup·squash 자동 메시지는 로컬 훅에서 제목 형식 검사를 생략한다.

## 코드와 검증

- 새 앱 코드는 Kotlin, 화면은 Jetpack Compose를 사용한다. 화면 상태와 SDK 접근을 분리하고, 차량 연동은 UI에서 직접 호출하지 않도록 별도 계층으로 둔다. Kotlin 공식 스타일을 따르고 이름은 타입과 UI를 그리는 `@Composable` 함수에 `UpperCamelCase`, 일반 함수·변수에 `lowerCamelCase`, 상수에 `UPPER_SNAKE_CASE`를 쓴다.
- Kotlin 포맷은 ktlint의 `android_studio` 스타일과 `.editorconfig`로 통일한다. `./gradlew ktlintFormat`으로 정리하고 `./gradlew ktlintCheck`로 확인한다. Kotlin 모듈을 추가할 때 해당 모듈에도 ktlint 플러그인을 적용한다.
- 시나리오를 바탕으로 상태 변환, 분기와 오류 처리 등 독립적인 Kotlin 로직에는 단위 테스트를 작성한다. SDK·차량 서비스 의존성은 대체 구현으로 분리하고, 테스트가 구현을 그대로 복제하지 않고 관찰 가능한 동작을 검증하도록 한다.
- 공통 최소 검증은 `./gradlew ktlintCheck lintDebug assembleDebug assembleDebugAndroidTest testDebugUnitTest`와 연결된 Pleos 에뮬레이터에서 `python3 scripts/run-device-tests.py`다. 후자는 테스트가 0개이거나 건너뛰어진 경우에도 실패한다. 여러 기기가 연결되어 있으면 실행 전 `ANDROID_SERIAL`을 지정한다. 실제 차량 상태 시나리오는 Virtual Console로도 확인한다. 각 시나리오의 자동 테스트 또는 수동 검증 결과와 미검증 사항을 PR에 적는다.
- Vehicle SDK 사용 시 도메인 capability, 필요한 권한 및 오류 처리를 확인한다. 실차 지원 여부를 에뮬레이터 결과만으로 단정하지 않는다. 키와 토큰은 소스나 Git에 넣지 않는다.
- GitHub Actions는 PR과 `main` push에서 GitHub 호스팅 러너로 ktlint·Android Lint·앱과 Android 테스트 APK 빌드를 실행하고, `mac-mini` self-hosted 러너에서 단위 테스트와 Pleos UI 테스트를 실행한다. 러너가 오프라인이면 해당 작업은 대기한다. CI 결과가 실제로 실행되기 전에는 통과했다고 주장하지 않는다.
- 단위 테스트 커버리지는 `./gradlew :app:unitTestCoverageReport`로 JaCoCo XML과 HTML을 생성한다. CI는 단위 테스트 커버리지와 JUnit 결과를 `unit` 플래그로, UI 테스트 JUnit 결과를 `ui` 플래그로 Codecov에 업로드한다. 테스트가 실패해도 생성된 결과를 업로드하며, CI 실패는 유지한다. 단위 테스트가 없으면 단위 테스트 커버리지 보고서는 생성되지 않는다. 커버리지 수집은 테스트 성공이나 Gherkin 시나리오 검증을 대신하지 않는다. UI 테스트 커버리지 수집은 아직 설정되지 않았다.

## 차량 연동 구현 선택

- 화면과 기록 로직은 `kr.roadcanvas.app.vehicle`의 `LocationProvider`와 `DrivingRestrictionProvider`에만 의존한다. 의존성은 `RoadCanvasApplication`이 만든 `AppContainer`(`kr.roadcanvas.app.di`)에서 받는다.
- 구현은 빌드 속성으로 고른다. `-Proadcanvas.location=simulated|pleos`, `-Proadcanvas.driving=simulated|aaos`, `-Proadcanvas.simRoute=circle|squished|line|stationary`. 예: `./gradlew assembleDebug -Proadcanvas.simRoute=line`. 허용하지 않는 값은 빌드 설정 단계에서 실패한다.
- 기본값은 debug가 위치 `simulated`, 주행 규제 `aaos`이고 release가 `pleos`, `aaos`다. 아직 구현되지 않은 구현을 고르면 모의 구현으로 대체하는 구조는 남아 있지만 현재 선택할 수 있는 값은 모두 구현되어 있다. debug 화면 오른쪽 아래에 사용 중인 구현과 현재 주행 규제 상태를 작게 표시하며, 대체한 경우 `(Pleos 미구현)`처럼 표시한다.
- 모의 위치는 위치 수신을 시작하면 경로를 처음부터 반복 재생한다. 기본값은 중심 서울시청(37.5665, 126.9780), 반지름 50m, 초속 5m(시속 18km), 재생 간격 1초다. 원은 63개 지점으로 약 63초에 한 바퀴, 직선 왕복은 동서 100m를 5m 간격으로 왕복(40개 지점)하고, 찌그러진 원은 중심 거리가 약 33~64m로 달라지며, 정지는 중심 한 지점을 반복한다. 값은 `SimulatedRouteConfig`에서 바꾼다.
- 모의 주행 규제는 항상 조작 가능 상태를 준다.
- `aaos` 주행 규제는 `kr.roadcanvas.app.vehicle.aaos`에서 `CarUxRestrictionsManager`를 구독한다. 수집자가 있을 때만 Car 서비스에 연결하고, Car API가 없거나 연결 실패·첫 값 미수신·예외면 확인 불가로 처리한다. 확인 불가는 조작 가능으로 보지 않는다.
- `pleos` 위치는 `kr.roadcanvas.app.vehicle.pleos`에서 FusedLocation SDK를 쓴다. 수집할 때마다 새 인스턴스로 지원 확인·초기화·리스너 등록을 하고 수집을 멈추면 해제한다. 에뮬레이터에서 앱은 사용자 10으로 실행되므로 권한은 `adb shell pm grant --user 10 kr.roadcanvas.app pleos.car.permission.FUSED_LOCATION`으로 준다. 확인 결과와 미검증 항목은 [FusedLocation 검증 결과](docs/pleos-fused-location-verification.md)에 있다.
- androidTest는 `RoadCanvasTestRunner`가 `TestRoadCanvasApplication`을 사용해 `FakeAppContainer`를 주입한다. 대체 구현은 `app/src/sharedTest/`에 있어 단위 테스트와 androidTest에서 함께 쓴다. 테스트에서 위치 상태·좌표와 주행 규제 상태를 원하는 시점에 바꿀 수 있다.

## 기기 저장

- 완료 기록과 진행 중 기록은 Room(KSP) DB `drawings.db`에 앱 전용 내부 저장소로 보관한다. 화면은 `kr.roadcanvas.app.drawing`의 `CompletedDrawingRepository`와 `InProgressDrawingRepository`만 `AppContainer`에서 받아 쓰고, 실패는 예외 대신 `StorageResult.Failure`로 받는다.
- 점수 세부 지표는 점수 계산 모델에 의존하지 않도록 이름과 값(`Map<String, Double>`)으로 저장한다.
- 스키마를 바꾸면 `DrawingDatabase.VERSION`을 올리고 `MIGRATIONS`에 마이그레이션을 추가한다. 빌드가 `app/schemas/`에 버전별 스키마를 기록하므로 함께 커밋한다. androidTest는 이 스키마로 마이그레이션을 검증한다.
- debug 빌드에서 `adb shell am start -n kr.roadcanvas.app/.debug.SeedDrawingsActivity`로 모의 원·찌그러진 원 경로의 완료 기록 두 건을 저장해 내 그림 화면을 확인한다. 실행할 때마다 추가되며 지우려면 앱 데이터를 삭제한다.
- 저장소 단위 테스트는 Robolectric과 in-memory DB로 실행한다. 테스트용 대체 구현은 `app/src/sharedTest/`의 `kr.roadcanvas.app.drawing.fake`에 있고 `failure`를 지정해 실패를 주입한다.

## 도전 기록

- 기록 세션은 `kr.roadcanvas.app.recording`의 `ChallengeRecorder` 하나를 앱 전체에서 공유하고, `AppContainer.recordingRunner`가 실행한다. 앱은 `foregroundServiceType="location"`인 `ChallengeRecordingService`에서, androidTest와 단위 테스트는 `app/src/sharedTest/`의 `FakeRecordingRunner`로 코루틴에서 실행한다.
- Android 14(targetSdk 34)에서 위치 타입 포그라운드 서비스는 `ACCESS_COARSE_LOCATION` 또는 `ACCESS_FINE_LOCATION`이 없으면 `SecurityException`으로 시작하지 못한다. Pleos 위치 권한만으로는 안 되는 것을 에뮬레이터에서 확인했으므로 시작 버튼을 누를 때 대략적인 위치 권한과 알림 권한을 요청한다. 에뮬레이터에서 직접 허용하려면 `adb shell pm grant --user 10 kr.roadcanvas.app android.permission.ACCESS_COARSE_LOCATION`을 쓴다.
- 서비스는 기록한 점 수를 logcat `ChallengeRecording` 태그로 남긴다. 주행 중 화면이 차단된 동안의 기록 여부는 `adb logcat -s ChallengeRecording`으로 확인한다.
- 진행 중 기록이 저장되어 있으면 앱을 다시 실행할 때 진행 화면으로 가서 이어서 기록한다. 종료하면 위치 구독만 멈추고 진행 중 기록은 남긴 채 시작·종료 시각을 결과 화면 경로 인자로 넘긴다. 결과 화면은 완료 기록 저장에 성공한 뒤 진행 중 기록을 지운다.

## 빌드와 실행

- JDK 17 이상과 Android SDK 플랫폼 35가 필요하다. 앱의 `minSdk`는 28, `targetSdk`는 34다.
- `./gradlew assembleDebug`로 빌드한다. 결과물은 `app/build/outputs/apk/debug/app-debug.apk`다.
- Android SDK와 JDK 경로는 각 개발 환경에서 설정한다. `local.properties`는 커밋하지 않는다.

## 네이버 지도

- 경로 지도는 네이버 지도 Android SDK(`com.naver.maps:map-sdk`)를 쓴다. 화면은 `kr.roadcanvas.app.map`의 `RouteMap`(좌표 목록과 `RouteMapOptions`)만 사용하고, 구현은 `AppContainer.routeMapComponent`로 주입한다. androidTest는 네트워크가 필요 없는 `FakeRouteMapComponent`를 쓴다.
- 키 등록: NCP 콘솔 Maps에서 Application을 등록하고 Dynamic Map을 선택한 뒤 Android 앱 패키지명 `kr.roadcanvas.app`을 등록한다. 발급된 Client ID(키 ID)를 환경 변수 `NAVER_MAP_CLIENT_ID` 또는 `local.properties`의 `NAVER_MAP_CLIENT_ID=<키 ID>`로 넣는다. 환경 변수가 우선한다. 키를 소스나 Git에 넣지 않는다.
- 키가 없으면 빈 값으로 빌드되고 지도 영역에 키 없음 안내를 표시한다. CI는 키 없이 빌드하고 테스트한다. 인증 실패(401, 429, 800 등)는 `RoadCanvasApplication`이 설정한 리스너가 로그(`NaverMapSetup` 태그)를 남기고 지도 영역에 오류 코드와 함께 안내한다.
- debug 빌드에서 `adb shell am start -n kr.roadcanvas.app/.debug.RouteMapPreviewActivity`로 모의 원 경로를 지도에 그린 미리보기를 연다. 앱 화면 흐름에는 포함되지 않는다.

## Pleos Connect Emulator

- Pleos Connect Emulator **v2.1.0**, Android API 34를 사용한다. AVD는 Cold Boot와 RAM 4GB로 설정한다. AVD 이름과 이미지 아키텍처는 각 환경에서 확인한다.
- `ANDROID_HOME`이 Android SDK 경로를 가리키도록 설정하고, 다음 명령으로 실행 및 연결을 확인한다.

```sh
"$ANDROID_HOME/emulator/emulator" -list-avds
"$ANDROID_HOME/emulator/emulator" -avd <AVD이름> -no-snapshot
# 다른 터미널에서
"$ANDROID_HOME/platform-tools/adb" devices
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n kr.roadcanvas.app/.MainActivity
```

- 첫 부팅에는 시간이 걸릴 수 있다. `adb shell getprop sys.boot_completed`가 `1`이면 부팅이 끝난 것이다.
- 에뮬레이터가 여러 대 연결돼 있으면 `adb -s <serial>`로 대상을 지정한다.

## PR 스크린샷 첨부

- 화면 변경을 검증할 때 `adb devices`로 대상 기기를 확인하고, `adb -s <기기번호> exec-out screencap -p > <임시이미지경로>`로 화면을 캡처한다. 이미지가 실제 변경을 보여주는지 확인한다. 주행 제한 화면이나 다른 앱 화면을 결과로 제출하지 않는다.
- 에뮬레이터 하나를 CI UI 테스트와 함께 쓰므로, 수동으로 설치한 `kr.roadcanvas.app`과 `kr.roadcanvas.app.test`는 확인이 끝나면 제거한다. 다른 기기에서 빌드한 설치본은 서명이 달라 CI 설치가 실패한다. CI도 테스트 전에 기존 설치본을 제거하지만, 테스트 실행 중에 수동으로 설치하면 그 실행은 실패할 수 있다.
- GitHub CLI의 `gh pr create --help`에서 `--attach` 지원을 확인한다. `gh` 2.103.3에서 PNG 첨부를 검증했다. 저장소 push 권한이 필요하다.
- PR 본문 파일에 `![설명](<이미지 경로>)`을 넣고 `gh pr create --body-file <본문파일> --attach <이미지파일>`로 같은 파일을 첨부한다. 경로에 공백이 있으면 따옴표로 감싼다. 본문에는 변경 사항, 검증과 `Closes #이슈번호`를 포함한다. 임시 이미지는 프로젝트에 커밋하지 않는다.
- 생성 후 `gh pr view <번호> --json body`로 이미지가 GitHub 첨부 주소로 바뀌었는지 확인한다. 첨부 실패 시 PR이 이미 생성되었을 수 있으므로 기존 PR을 확인한 후 재시도한다. 첨부할 수 없으면 이유를 보고하고 확인하지 않은 결과를 적지 않는다.

## Virtual Console

요청 주소와 파라미터는 [OpenAPI 명세](docs/pleos-console/openapi-v2.1.0.json)와 [요청 방법](docs/pleos-console/README.md)을 사용하고, 검증 범위와 미해결 사항은 [항목별 검증 결과](docs/pleos-console/verification-v2.1.0.md)를 확인한다. 요청이 실패하거나 명세와 다를 때만 실행 중인 콘솔의 `/vehicle/catalog`과 페이지 코드를 확인하고 명세를 갱신한다.

에뮬레이터가 실행 중일 때 별도 터미널에서 아래 명령을 실행하고 브라우저에서 <http://127.0.0.1:8088/>을 연다. `ANDROID_HOME`은 해당 터미널에도 설정해야 한다.

```sh
"$ANDROID_HOME/platform-tools/adb" forward tcp:8088 tcp:8088
```

- 콘솔 페이지를 열어둬야 차량 상태 입력이 활성화된다. 닫힌 상태에서 기어 변경을 시도하면 `503 emulator_inactive`가 반환되는 것을 확인했다.
- P·R·N·D, 가속·브레이크와 스티어링 각도를 조작할 수 있다. 주행 화면은 D 선택 후 ACCEL을 길게 눌러 시험한다.
- 주행 상태 검증이 필요하면 대상 에뮬레이터와 현재 차량 상태를 먼저 확인한다. Virtual Console을 열고 기어를 D로 변경한 뒤, 콘솔 표시와 앱의 실제 반응을 모두 확인한다. D 변경만으로 속도가 생기지는 않으므로 속도가 필요한 경우에만 ACCEL을 조작한다. 테스트 종료 후 직접 변경한 기어·페달 값은 가능한 한 원래 상태로 복원한다.
- 브라우저 조작 도구가 없으면 현재 버전에서 확인된 콘솔의 비공식 로컬 HTTP 인터페이스를 사용할 수 있다. `GET /vehicle/state`에서 `gear.bridge_ready`와 `gear.actual.value`를 확인하고, `PUT /vehicle/gear?gear=Drive` 또는 `gear=Park`로 변경한 뒤 상태를 다시 조회한다. 이 인터페이스는 SDK의 공식 API가 아니므로 버전마다 달라질 수 있다.
- 브라우저 페이지가 열려 있지 않으면 로컬 인터페이스를 사용할 때 `POST /browser/session?client=<고유식별자>`를 1초 간격으로 보내 연결을 유지해야 한다. 확인된 콘솔의 세션 제한은 3초다. 테스트가 끝나면 `POST /browser/session?client=<고유식별자>&closing=1`로 자신이 만든 세션만 닫는다. 이미 사용 중인 다른 세션은 건드리지 않는다. `/vehicle/state`의 `bridge_ready`가 false이거나 요청이 실패하면 성공으로 간주하지 말고 오류를 기록한다.
- 테스트 대상 상태가 D에서 표시 제한되는 앱이라면 그 제한 화면을 결과로 보고, 주행 중 기능을 사용했다고 주장하지 않는다. 스크린샷도 요청한 화면이 실제로 보이는지 확인한다.
- P 상태에서 왼쪽 위 Theme 버튼으로 Highway와 Forest를 전환한다. Highway에서는 1인칭과 3인칭을 선택할 수 있다. 교차로 좌회전·우회전이나 실제 경로 주행 기능은 확인되지 않았다.
- `Emulation Controls`에서 차량 상태를 도메인별로 설정해 앱 반응을 시험한다. 연결이 안 되면 `adb devices`, 포트 포워딩, 에뮬레이터 부팅 상태를 확인한다.

공식 개발 문서의 검색용 사본은 `docs/pleos/`에 있다. 세부 API와 최신 정보는 각 파일에 기재된 원문을 확인한다. [Virtual Console 공식 설정 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)도 참고한다.
