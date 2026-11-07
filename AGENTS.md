# Pleos 개발 환경

이 프로젝트는 Pleos Connect 차량용 앱이다. 개발 스택은 **Kotlin과 Jetpack Compose**다. 현재는 Hello World 단계이며 Pleos SDK는 아직 연동하지 않았다.

## 작업 방식

- 모든 일반 작업은 GitHub 이슈를 먼저 만들고, 이슈별 브랜치에서 작업한 후 PR로 `main`에 반영한다. 긴급하거나 사용자가 다른 절차를 명시한 경우 그 지시를 따른다.
- 이슈를 만들기 전에 기존 이슈를 검색해 중복을 피한다. 제목은 구체적인 작업이나 문제를 한 줄로 적는다. 이슈 템플릿의 **배경**과 체크 가능한 **완료 조건**을 채우고 필요한 경우 작업 범위, 재현 방법과 참고 자료를 덧붙인다. 범위가 크면 이슈를 나눈다. 사용 가능한 라벨과 담당자가 있으면 지정하고, 없으면 억지로 만들지 않는다.
- `main`을 최신 상태로 확인한 뒤 `feat/123-short-description`, `fix/123-short-description`, `chore/123-short-description`처럼 이슈 번호와 영문 소문자·하이픈을 사용해 브랜치를 만든다. **한 이슈당 브랜치 하나와 PR 하나**를 원칙으로 한다.
- PR 제목은 커밋 제목과 같은 형식으로 작성한다. PR 템플릿의 **변경 사항**과 **검증**에 변경 이유, 검증 결과와 미확인 사항을 적고 필요한 경우 스크린샷을 추가한다. `Closes #123`으로 이슈를 연결한다. 관련 없는 변경은 섞지 않는다.
- 혼자 작업하므로 별도 리뷰 승인은 요구하지 않는다. PR의 변경 사항과 검증 결과를 직접 확인하고 필수 검증 통과 후 squash merge한다. 브랜치 보호나 필수 체크는 저장소 설정에서 별도 활성화해야 한다.
- README는 제출용이다. 사용자가 명시적으로 요청하지 않으면 변경하지 않는다. 개발 절차와 환경 정보는 이 파일에 기록한다.

## 커밋 컨벤션

- 제목은 `type: 설명` 형식이다. 허용 타입: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`, `revert`. 제목은 72자 이내이며 설명은 한국어를 기본으로 구체적으로 적는다. 고유명사나 기술 용어는 영문을 사용할 수 있다. 예: `feat: 주행 상태 표시 추가`.
- 이슈 번호는 커밋 제목에 강제하지 않는다. PR 본문의 `Closes #번호`로 연결한다. 의미 있는 단위로 커밋하고 비밀 정보나 생성된 빌드 산출물을 포함하지 않는다.
- 이슈·커밋·PR의 제목과 본문에 이모지, `Generated with Claude Code`, LLM 도구가 붙이는 `Co-Authored-By` 같은 자동 서명이나 출처 표기를 넣지 않는다. 모델명, 추론 강도(effort), 사용한 에이전트 등 작업 도구 정보도 적지 않는다. 변경 내용과 검증 결과에만 집중한다.
- 최초 한 번 `git config --local core.hooksPath .githooks`를 실행해 저장소 훅을 활성화한다. Python 3이 필요하다. `commit-msg` 훅이 제목 형식과 메시지의 일부 이모지·자동 서명을 검사한다. 모든 표현을 자동으로 잡을 수는 없으므로 작성 시 직접 확인한다. 훅은 복제 시 자동 활성화되지 않으며, CI는 PR 제목도 같은 규칙으로 검사한다. merge·revert·fixup·squash 자동 메시지는 로컬 훅에서 제목 형식 검사를 생략한다.

## 코드와 검증

- 새 앱 코드는 Kotlin, 화면은 Jetpack Compose를 사용한다. 화면 상태와 SDK 접근을 분리하고, 차량 연동은 UI에서 직접 호출하지 않도록 별도 계층으로 둔다. Kotlin 공식 스타일을 따르고 이름은 타입과 UI를 그리는 `@Composable` 함수에 `UpperCamelCase`, 일반 함수·변수에 `lowerCamelCase`, 상수에 `UPPER_SNAKE_CASE`를 쓴다.
- Kotlin 포맷은 ktlint의 `android_studio` 스타일과 `.editorconfig`로 통일한다. `./gradlew ktlintFormat`으로 정리하고 `./gradlew ktlintCheck`로 확인한다. Kotlin 모듈을 추가할 때 해당 모듈에도 ktlint 플러그인을 적용한다.
- 공통 최소 검증은 `./gradlew ktlintCheck lintDebug assembleDebug`이며, UI·차량 상태 관련 변경은 가능한 경우 Pleos 에뮬레이터와 Virtual Console로 확인하고 미검증 사항을 PR에 적는다. 단위 테스트가 있으면 `./gradlew testDebugUnitTest`도 실행하며, 필요한 경우 의미 있는 테스트를 추가한다.
- Vehicle SDK 사용 시 도메인 capability, 필요한 권한 및 오류 처리를 확인한다. 실차 지원 여부를 에뮬레이터 결과만으로 단정하지 않는다. 키와 토큰은 소스나 Git에 넣지 않는다.
- GitHub Actions는 PR과 `main` push에서 ktlint·Android Lint·Android 빌드·단위 테스트를 실행한다. Pleos 에뮬레이터 검증은 자동화하지 않는다. CI 결과가 실제로 실행되기 전에는 통과했다고 주장하지 않는다.

## 빌드와 실행

- JDK 17 이상과 Android SDK 플랫폼 35가 필요하다. 앱의 `minSdk`는 28, `targetSdk`는 34다.
- `./gradlew assembleDebug`로 빌드한다. 결과물은 `app/build/outputs/apk/debug/app-debug.apk`다.
- 이 Mac에서는 SDK가 `/opt/homebrew/share/android-commandlinetools`, JDK 21이 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`에 있다. 다른 환경에서는 경로를 확인한다. `local.properties`는 커밋하지 않는다.

## Pleos Connect Emulator

- 설치된 이미지는 Pleos Connect Emulator **v2.1.0**, Android API 34, ARM64다. AVD 이름은 `Pleos_Connect`이며 Cold Boot와 RAM 4GB로 설정했다.
- 이 Mac에서 실행 및 연결을 확인하는 명령은 다음과 같다.

```sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
"$ANDROID_HOME/emulator/emulator" -avd Pleos_Connect -no-snapshot
# 다른 터미널에서
"$ANDROID_HOME/platform-tools/adb" devices
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n kr.roadcanvas.app/.MainActivity
```

- 첫 부팅에는 시간이 걸릴 수 있다. `adb shell getprop sys.boot_completed`가 `1`이면 부팅이 끝난 것이다.
- 에뮬레이터가 여러 대 연결돼 있으면 `adb -s <serial>`로 대상을 지정한다.

## Virtual Console

에뮬레이터가 실행 중일 때 별도 터미널에서 아래 명령을 실행하고 브라우저에서 <http://127.0.0.1:8088/>을 연다. `ANDROID_HOME`은 해당 터미널에도 설정해야 한다.

```sh
"$ANDROID_HOME/platform-tools/adb" forward tcp:8088 tcp:8088
```

- 콘솔 페이지를 열어둬야 차량 상태 입력이 활성화된다. 닫힌 상태에서 기어 변경을 시도하면 `503 emulator_inactive`가 반환되는 것을 확인했다.
- P·R·N·D, 가속·브레이크와 스티어링 각도를 조작할 수 있다. 주행 화면은 D 선택 후 ACCEL을 길게 눌러 시험한다.
- P 상태에서 왼쪽 위 Theme 버튼으로 Highway와 Forest를 전환한다. Highway에서는 1인칭과 3인칭을 선택할 수 있다. 교차로 좌회전·우회전이나 실제 경로 주행 기능은 확인되지 않았다.
- `Emulation Controls`에서 차량 상태를 도메인별로 설정해 앱 반응을 시험한다. 연결이 안 되면 `adb devices`, 포트 포워딩, 에뮬레이터 부팅 상태를 확인한다.

공식 개발 문서의 검색용 사본은 `docs/pleos/`에 있다. 세부 API와 최신 정보는 각 파일에 기재된 원문을 확인한다. [Virtual Console 공식 설정 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)도 참고한다.
