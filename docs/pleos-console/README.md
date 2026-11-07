# Pleos Virtual Console 요청 방법

Pleos Connect Emulator v2.1.0에서 확인한 **비공식 로컬 인터페이스**다. 앱에서 사용하는 Pleos SDK API가 아니며 에뮬레이터 버전에 따라 달라질 수 있다. 에뮬레이터가 실행 중이고 `adb`가 연결되어 있어야 한다.

## 1. 연결과 세션 유지

```sh
adb devices
adb -s <에뮬레이터번호> forward tcp:8088 tcp:8088
```

Virtual Console을 브라우저에서 `http://127.0.0.1:8088/`로 열어두거나, 브라우저 없이 요청하려면 아래처럼 세션을 유지한다. 세션은 약 3초 후 만료되므로 작업하는 동안 반복 전송해야 한다. 고유한 세션 이름을 사용하고 다른 세션을 종료하지 않는다.

```sh
CONSOLE_URL=http://127.0.0.1:8088
CONSOLE_SESSION="agent-$(date +%s)-$$"
export CONSOLE_URL CONSOLE_SESSION
(
  while :; do
    curl -fsS -X POST "$CONSOLE_URL/browser/session?client=$CONSOLE_SESSION" >/dev/null || break
    sleep 1
  done
) &
CONSOLE_HEARTBEAT_PID=$!
```

같은 셸에서 아래 요청을 실행한다. 중단하거나 종료할 때도 다음 두 명령을 실행한다.

```sh
kill "$CONSOLE_HEARTBEAT_PID"
curl -fsS -X POST "$CONSOLE_URL/browser/session?client=$CONSOLE_SESSION&closing=1"
```

브라우저를 쓰는 경우에는 브라우저가 세션을 유지하므로 위 반복 명령이 필요 없다. 여러 에뮬레이터가 연결되어 있다면 포워딩과 캡처에 같은 기기 번호를 지정한다.

## 2. 조회와 기어 변경

```sh
# 전체 상태와 기능 목록
curl -fsS "$CONSOLE_URL/vehicle/state"
curl -fsS "$CONSOLE_URL/vehicle/catalog"

# 현재 기어와 연결 상태
curl -fsS "$CONSOLE_URL/vehicle/state" | python3 -c 'import json,sys; print(json.load(sys.stdin)["gear"])'

# 기어 변경: Park, Reverse, Neutral, Drive
curl -fsS -X PUT "$CONSOLE_URL/vehicle/gear?gear=Drive"
curl -fsS -X PUT "$CONSOLE_URL/vehicle/gear?gear=Park"
```

P→D→P 전환은 실제 에뮬레이터의 기어 표시와 화면 변화로 확인했다. 변경 응답뿐 아니라 `/vehicle/state`의 `gear.bridge_ready`와 `gear.actual.value`, 앱 화면을 확인한다. D 상태에서 주행 제한 또는 차량 경고가 앱을 가릴 수 있다. `503 emulator_inactive`가 나오면 세션과 포트 포워딩을 확인한다.

## 3. 다른 차량 상태 제어

전체 요청 경로·필드·선택값·영역은 [OpenAPI 명세](openapi-v2.1.0.json)를 사용한다. 경로별 `x-console-features`에 기능마다 필요한 필드·고정값·영역·범위가 있다. 콘솔에서 추출한 [원본 목록](catalog-v2.1.0.json)도 함께 보관한다. 항목별 변경 요청은 모두 `PUT`이며 예시는 다음과 같다.

```sh
# 브레이크 페달 위치 0~100%
curl -fsS -X PUT "$CONSOLE_URL/vehicle/controls?brake=50"

# 앞좌석 왼쪽 도어 열기 / 닫기
curl -fsS -X PUT "$CONSOLE_URL/vehicle/body?door_fl=true"
curl -fsS -X PUT "$CONSOLE_URL/vehicle/body?door_fl=false"
```

브레이크와 도어를 포함한 검증 결과는 [항목별 검증 결과](verification-v2.1.0.md)에 있다. 대부분은 HTTP 요청 후 콘솔 상태값만 확인했다. 이는 앱의 SDK 수신이나 실차 동작까지 검증한 것은 아니다. 영역이 있는 항목은 기록된 영역 하나만 확인했다.

주행 거리와 주행 시간은 감소 요청을 서버가 거부한다. 값을 올리면 원래 값으로 되돌리지 못하므로 시험 전에 유의한다. 숨김 좌석 이동 명령 5개는 요청 성공에도 상태값이 바뀌지 않아 미해결이다. 나머지 미검증 항목은 결과 파일에 표시되어 있다. 요청이 실패하거나 명세와 다르면 실행 중인 콘솔의 `/vehicle/catalog`과 페이지 코드를 확인해 원인을 파악하고 명세를 갱신한다.
