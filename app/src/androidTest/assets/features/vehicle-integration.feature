Feature: 차량 연동
  앱은 차량 위치와 주행 규제 상태를 하나의 차량 연동 계층에서 받는다.
  테스트는 대체 구현으로 상태를 바꾸고, 에뮬레이터 시연은 모의 구현을 사용한다.

  Issues:
  - [#9 차량 연동 추상화 계층과 모의 구현 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/9)
  - [#13 CarUxRestrictions 주행 규제 구현 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/13)
  - [#12 FusedLocation SDK 위치 구현 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/12)

  Scenario: 디버그 화면에 사용 중인 차량 연동 구현을 표시한다
    Given 앱을 실행한다
    Then 화면에 "위치: 테스트 · 주행 규제: 테스트"가 표시된다

  Scenario Outline: 테스트에서 위치 사용 상태를 바꾼다
    Given 테스트용 차량 연동이 주입되어 있다
    When 테스트에서 위치 상태를 "<상태>"로 바꾼다
    Then 앱이 받는 위치 상태는 "<상태>"이다

    Examples:
      | 상태    |
      | 사용 가능 |
      | 미지원   |
      | 접근 차단 |
      | 오류    |

  Scenario: 위치 권한이 필요하면 요청할 권한 이름을 알 수 있다
    Given 테스트용 차량 연동이 주입되어 있다
    When 테스트에서 위치 상태를 "권한 필요"로 바꾼다
    Then 앱이 받는 위치 상태는 "권한 필요"이다
    And 요청할 권한에 "pleos.car.permission.FUSED_LOCATION"이 포함된다

  Scenario: 테스트에서 원하는 시점에 위치 좌표를 보낸다
    Given 테스트용 차량 연동이 주입되어 있다
    And 앱이 위치 수신을 시작했다
    When 테스트에서 위도 37.5665, 경도 126.978 위치를 보낸다
    Then 앱이 위도 37.5665, 경도 126.978 위치를 받는다

  Scenario Outline: 테스트에서 주행 규제 상태를 바꾼다
    Given 테스트용 차량 연동이 주입되어 있다
    When 테스트에서 주행 규제 상태를 "<상태>"로 바꾼다
    Then 앱이 받는 주행 규제 상태는 "<상태>"이다

    Examples:
      | 상태    |
      | 조작 가능 |
      | 조작 제한 |
      | 확인 불가 |

  Scenario: 모의 위치는 기록을 시작하면 경로를 1초 간격으로 재생한다
    Given 모의 위치 경로를 "circle"로 고른다
    When 모의 위치 기록을 시작해 위치 3개를 받는다
    Then 받은 위치는 경로의 처음 3개 지점이다
    And 위치 사이 간격은 약 1초다

  Scenario: 모의 주행 규제는 항상 조작 가능 상태다
    Given 주행 규제 구현으로 "simulated"를 고른다
    When 차량 연동을 구성한다
    Then 앱이 받는 주행 규제 상태는 "조작 가능"이다

  Scenario: 실제 구현을 고르면 모의 구현으로 대체하지 않는다
    Given 위치 구현으로 "pleos"를 고른다
    And 주행 규제 구현으로 "aaos"를 고른다
    When 차량 연동을 구성한다
    Then 사용 중인 구현 표시는 "위치: Pleos · 주행 규제: AAOS"이다
