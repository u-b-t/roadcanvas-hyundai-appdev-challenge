# FusedLocation 검증 결과

FusedLocation SDK `ai.pleos.playground:FusedLocation:1.1.3`을 Pleos Connect Emulator v2.1.0(API 34)에서 확인한 결과다. 2026-11-08에 `-Proadcanvas.location=pleos`로 빌드한 debug 앱에 위치 상태와 받은 위치를 logcat에 남기는 임시 코드를 넣어 확인했다. 임시 코드는 커밋하지 않았다.

## 확인한 항목

| 항목 | 결과 |
| --- | --- |
| `isSupported()` | `true`. SDK는 `ai.pleos.playground.service.location.fused` 패키지가 설치되어 있는지로 판단한다. |
| 권한 미허용 | 앱 상태가 `PermissionRequired([pleos.car.permission.FUSED_LOCATION])`가 되고 위치 수집이 끝난다. |
| 권한 허용 | 앱은 사용자 10(`am get-current-user`)으로 실행된다. `pm grant --user 10 kr.roadcanvas.app pleos.car.permission.FUSED_LOCATION`으로 허용해야 하며 사용자 0의 권한은 영향이 없다. |
| 수신 여부·주기 | 허용 후 다시 실행하면 상태가 `Available`이 되고 약 1초 간격으로 위치를 받는다. 위치를 움직이지 않아도 같은 좌표가 계속 온다. |
| 시각 | `LocationData.timestampMillis`는 Unix epoch 밀리초이며 수신 시각과 수 ms 차이였다. |
| `adb emu geo fix <경도> <위도>` | 다음 수신(1초 이내)부터 바뀐 좌표가 온다. 기본 좌표는 위도 37.421998, 경도 -122.084였고, 마지막으로 위도 37.4979, 경도 127.0276을 설정했다. |
| 시스템 위치 끄기 | `cmd location set-location-enabled false --user 10` 동안에는 위치도 `onError`도 오지 않았고, 다시 켜면 수신이 이어졌다. |
| 차단 안내 Intent | `showLocationAccessBlockedPopup()`이 여는 `ai.umos.intent.action.SENSOR_PRIVACY_DIALOG`는 `ai.umos.systemdialogdispatcher/.ui.SystemDialogActivity`로 연결된다. |

## SDK 동작(바이트코드 확인)

- `registerFusedLocationCallback`, `unregisterFusedLocationCallback`, `getLastKnownLocation`은 권한이 없으면 `SecurityException("need Permission...")`을 바로 던진다.
- 서비스 오류는 `onError(Exception(메시지))`로 오며 오류 종류를 구분하는 타입이 없다.
- 서비스가 위치 없이 알리면 SDK가 로그만 남기고 `onChanged`를 부르지 않는다. 앱은 문서대로 `onChanged(null)`도 무시한다.
- `showLocationAccessBlockedPopup()`은 SDK에 넘긴 Context로 `startActivity`를 부르고 `FLAG_ACTIVITY_NEW_TASK`를 붙이지 않는다. 그래서 앱은 안내용 인스턴스를 Activity Context로 따로 만든다.
- SDK manifest가 `android.hardware.type.automotive`를 `required="true"`로 선언해 병합된 manifest도 필수가 된다.

## 미검증

- 위치 접근 차단 상태. 에뮬레이터에서 차단 상태를 만드는 방법과 그때의 오류 메시지를 확인하지 못했다. 서비스 APK는 shell 권한으로 읽을 수 없었다. 앱은 오류 메시지에 `block` 또는 `privacy`가 있으면 차단으로 본다.
- 차단 안내 팝업이 실제로 뜨는지.
- 권한 요청 화면(#17)에서 허용한 뒤 수신이 다시 시작되는지.
- 실차에서의 지원 여부와 수신 주기.
- 포그라운드 서비스에서의 장시간 수신.
