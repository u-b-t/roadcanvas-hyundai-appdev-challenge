# 에뮬레이터 메모리 측정

Pleos 에뮬레이터(qemu)는 실행하는 동안 메모리를 계속 늘린다(#135). CI는 `scripts/ci-emulator.sh`가 qemu 메모리를 확인해 기준을 넘으면 재시작한다.

## 지표

- `top -l 1 -pid <qemu pid> -stats pid,mem,cmprs`의 `MEM`으로 판정한다. 압축·스왑으로 밀려난 메모리를 포함한다.
- `ps -o rss=`는 압축된 메모리를 빼서 실제보다 작게 보인다. 아래 맥미니 예에서 RSS는 약 1.0GB였다.
- `footprint -p <pid>`는 같은 시점에 5,057MB로 `top` MEM보다 작게 보고했다. 차이의 원인은 확인하지 않았다.

## 확인한 값

| 환경 | 렌더링 | 상황 | top MEM | 압축 | ps RSS |
| --- | --- | --- | --- | --- | --- |
| 맥미니(8GB) | `-no-window -gpu swiftshader_indirect` | 콜드 부팅 직후 | 약 1.6GB | - | - |
| 맥미니(8GB) | `-no-window -gpu swiftshader_indirect` | 띄운 지 48분, UI 테스트 한 번(약 20분) 뒤 | 8,126MB | 7,080MB | 1,055MB |
| 맥북(16GB) | 창 모드 GPU 렌더링 | 장시간 사용 | 43GB까지 증가 후 재부팅 | - | - |

## 렌더링 옵션 비교

미측정. `-gpu guest`, `-gpu off`의 메모리 증가량과 Pleos 서비스·`car_service` 정상 여부는 측정하지 않았다. 기본값은 기존 `swiftshader_indirect`를 유지한다. 측정하려면 CI가 쓰지 않는 동안 맥미니에서 `PLEOS_EMULATOR_GPU=<옵션> scripts/ci-emulator.sh prepare`로 콜드 부팅하고, 같은 기능 파일로 `scripts/run-device-tests.py --feature ...`를 실행하기 전후의 `[ci-emulator]` 메모리 로그(`check-memory`)를 비교한다.

## 기준

- CI: `PLEOS_EMULATOR_MEMORY_LIMIT_MB`(기본 3072). `prepare`와 UI 테스트 뒤 `check-memory`에서 넘으면 종료하고 콜드 부팅한다.
- 로컬 `scripts/dev-emulator.sh`: 6GB를 넘으면 경고만 한다.
