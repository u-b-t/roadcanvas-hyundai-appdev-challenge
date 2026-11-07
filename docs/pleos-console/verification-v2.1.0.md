# Virtual Console 제어 검증 결과

Pleos Connect Emulator v2.1.0에서 콘솔 HTTP 요청과 `/vehicle/state` 변화를 확인했다. 대부분 앱의 SDK 수신, 실제 차량 데이터 또는 UI 반응은 검증하지 않았다. 영역이 있는 항목은 아래에 적은 한 영역만 확인했다.

## 결과

- 54개: 변경 요청 후 콘솔 상태값 변경을 확인했다. 기어는 화면 변화도 확인했다.
- 5개: 좌석 이동 요청은 성공했지만 요청 직후 조회값이 바뀌지 않았다. 원인은 미확인이다.
- 4개: 일회성 이벤트이거나 기존 상태를 확인하지 못해 시험하지 않았다.

## 주의할 점

- **주행 거리와 주행 시간:** 각각 0에서 1km, 1분으로 변경한 뒤 원래 값으로 복원하려 했으나, 서버가 감소 요청을 HTTP 400으로 거부했다. 두 값은 시험으로 1 증가한 상태다.
- **조향 각도:** 시험 당시 기존 값은 540도였고 500도로 변경 후 540도로 복원했다. 카탈로그 범위는 -470~500도지만 콘솔 메인 화면 코드는 -540~540도를 사용한다.
- **좌석 이동:** 운전석의 전후·등받이·높이·기울기·요추 이동에 -1을 요청했으나 조회값은 모두 0이었다. 각 이동 명령은 0으로 복원했다.
- **미검증:** ETCS 시나리오는 일회성 이벤트라 실행하지 않았다. 전원 상태·경고등·알림은 초기 상태가 없어 복원 여부를 확인할 수 없어서 시험하지 않았다.

## 항목별 결과

| 구분 | 항목 | 결과 | 시험 영역 |
| --- | --- | --- | --- |
| Motion | Gear Selection (`gear-selection`) | 상태 변경 확인 | — |
| Motion | Brake Pedal (`brake-pedal-status`) | 상태 변경 확인 | — |
| Convenience | ETCS Power (`etcs-activated`) | 상태 변경 확인 | — |
| Convenience | ETCS Scenario (`etcs-scenario`) | 미검증 | — |
| Energy | Power System Actual (`power-system-actual`) | 미검증 | — |
| Safety | Telltales (`telltales`) | 미검증 | ParkingBrake |
| Vehicle | Notification 1 (`notification-1`) | 미검증 | — |
| Convenience | Door Position (`door-pos`) | 상태 변경 확인 | front_left |
| Convenience | Door Lock (`door-lock`) | 상태 변경 확인 | — |
| Convenience | Trunk / Frunk Position (`trunk-position`) | 상태 변경 확인 | frunk |
| ADAS | Cruise Control State (`cruise-control-state`) | 상태 변경 확인 | — |
| ADAS | Cruise Target Speed (`cruise-control-target-speed`) | 상태 변경 확인 | — |
| Motion | Odometer (`perf-odometer`) | 상태 변경 확인 | — |
| Motion | Trip Driving Time (`trip-driving-time`) | 상태 변경 확인 | — |
| Energy | Remaining Range (`range-remaining`) | 상태 변경 확인 | — |
| Energy | Battery Energy (`ev-battery-level`) | 상태 변경 확인 | — |
| Energy | Battery SOC (`ev-battery-soc`) | 상태 변경 확인 | — |
| Energy | Battery Capacity (`ev-battery-capacity`) | 상태 변경 확인 | — |
| Energy | Charge Current Limit (`ev-charge-current-limit`) | 상태 변경 확인 | — |
| Energy | Charge Percent Limit (`ev-charge-percent-limit`) | 상태 변경 확인 | — |
| Energy | Charge Switch (`ev-charge-switch`) | 상태 변경 확인 | — |
| Energy | Charge Port (`ev-charge-port`) | 상태 변경 확인 | — |
| Misc | Tire Pressure (`tire-pressure`) | 상태 변경 확인 | 1114113 |
| Motion | Steering Angle (`steering-angle`) | 상태 변경 확인 | — |
| Motion | Steering Wheel Heat (`steering-wheel-heat`) | 상태 변경 확인 | — |
| Climate | HVAC Power (`hvac-power`) | 상태 변경 확인 | — |
| Climate | Air Conditioning (`hvac-ac`) | 상태 변경 확인 | — |
| Climate | HVAC Auto (`hvac-auto`) | 상태 변경 확인 | — |
| Climate | HVAC Dual (`hvac-dual`) | 상태 변경 확인 | — |
| Climate | Air Recirculation (`hvac-recirc`) | 상태 변경 확인 | — |
| Climate | Fan Speed (`hvac-fan-speed`) | 상태 변경 확인 | — |
| Climate | Fan Direction (`hvac-fan-direction`) | 상태 변경 확인 | driver |
| Climate | Defroster (`hvac-defroster`) | 상태 변경 확인 | front_defroster_on |
| Climate | Target Temperature (`hvac-temperature-set`) | 상태 변경 확인 | driver |
| Climate | Seat Temperature (`hvac-seat-temperature`) | 상태 변경 확인 | driver |
| Climate | Air Cleaner (`hvac-air-cleaner`) | 상태 변경 확인 | — |
| Convenience | Cabin Lights Switch (`cabin-lights-switch`) | 상태 변경 확인 | — |
| Convenience | Reading Lights (`reading-lights-switch`) | 상태 변경 확인 | front_left |
| Convenience | Ambient Light Switch (`ambient-light-switch`) | 상태 변경 확인 | — |
| Convenience | Ambient Brightness (`ambient-light-brightness`) | 상태 변경 확인 | — |
| Convenience | Ambient Color (`ambient-light-color`) | 상태 변경 확인 | — |
| Convenience | Exterior Lights Mode (`exterior-lights-mode`) | 상태 변경 확인 | — |
| Convenience | High Beam (`high-beam-lights`) | 상태 변경 확인 | — |
| Convenience | Hazard Lights (`hazard-lights`) | 상태 변경 확인 | — |
| Convenience | Stop Lights (`stop-lights`) | 상태 변경 확인 | — |
| Convenience | Door Lights (`door-lights`) | 상태 변경 확인 | frunk |
| Convenience | Escort Lights (`escort-lights`) | 상태 변경 확인 | — |
| Convenience | Turn Signal State (`turn-signal-state`) | 상태 변경 확인 | — |
| Convenience | Window Position (`window-position`) | 상태 변경 확인 | front_left |
| Convenience | Window Lock (`window-lock`) | 상태 변경 확인 | all |
| Convenience | Seat Fore/Aft Position (`seat-fore-aft-position`) | 상태 변경 확인 | driver |
| Convenience | Seat Fore/Aft Move (`seat-fore-aft-move`) | 상태 불일치 | driver |
| Convenience | Seat Backrest Position (`seat-backrest-position`) | 상태 변경 확인 | driver |
| Convenience | Seat Backrest Move (`seat-backrest-move`) | 상태 불일치 | driver |
| Convenience | Seat Height Position (`seat-height-position`) | 상태 변경 확인 | driver |
| Convenience | Seat Height Move (`seat-height-move`) | 상태 불일치 | driver |
| Convenience | Seat Tilt Position (`seat-tilt-position`) | 상태 변경 확인 | driver |
| Convenience | Seat Tilt Move (`seat-tilt-move`) | 상태 불일치 | driver |
| Convenience | Seat Lumbar Position (`seat-lumbar-position`) | 상태 변경 확인 | driver |
| Convenience | Seat Lumbar Move (`seat-lumbar-move`) | 상태 불일치 | driver |
| Convenience | Seat Occupancy (`seat-occupancy`) | 상태 변경 확인 | driver |
| Convenience | Windshield Wiper Switch (`windshield-wipers-switch`) | 상태 변경 확인 | front |
| Convenience | Glove Box (`glove-box-door-pos`) | 상태 변경 확인 | — |
