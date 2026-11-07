# RoadCanvas

Pleos Connect 차량용 앱을 위한 프로젝트입니다. 현재 Android 앱을 실행하기 위한 Hello World 단계입니다.

## 빌드

JDK 17 이상과 Android SDK 플랫폼 35가 필요합니다. Android SDK 경로는 환경 변수 `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`로 지정합니다.

```sh
./gradlew assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 이 앱은 아직 Pleos SDK를 연동하지 않았습니다.

## 참고 자료

로컬에서 검색할 수 있는 [Pleos 개발 문서](docs/pleos/README.md)를 함께 정리했습니다.
