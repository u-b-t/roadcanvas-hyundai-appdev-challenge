Feature: 주행 규제
  앱은 플랫폼이 알려주는 기어와 속도로 화면 조작 가능 여부를 판단한다.
  P이거나 속도가 1km/h 미만이면 D에서 멈춘 상태도 정차로 본다.
  기어가 P가 아니고 속도를 받을 수 없으면 CarUxRestrictions로 판단한다.
  확인할 수 없는 상태는 정차로 취급하지 않는다.

  Issues:
  - [#150 주행 규제 기어와 속도 시나리오 단계 연결 수정](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/150)
  - [#13 CarUxRestrictions 주행 규제 구현 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/13)
  - [#119 D에서 멈춘 상태를 정차로 보도록 주행 상태 기준 변경](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/119)
  - [#131 주행 상태 판단을 기어·속도 기준으로 바꿔 D 정차 인정](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/131)

  Scenario Outline: 기어와 속도로 앱의 주행 규제 상태를 정한다
    Given Car 서비스의 현재 주행 최적화 필요 여부가 "필요"이다
    And 앱이 AAOS 주행 규제를 구독하고 있다
    When 플랫폼이 기어 "<기어>"와 속도 <속도>km/h를 알린다
    Then AAOS 주행 규제 상태는 "<상태>"이다

    Examples:
      | 기어 | 속도  | 상태    |
      | P  | 0   | 조작 가능 |
      | D  | 0   | 조작 가능 |
      | D  | 0.5 | 조작 가능 |
      | N  | 0   | 조작 가능 |
      | D  | 5   | 조작 제한 |
      | R  | 3   | 조작 제한 |

  Scenario Outline: 속도를 받지 못하면 P는 조작 가능이고 다른 기어는 UX 규제로 판단한다
    Given Car 서비스의 현재 주행 최적화 필요 여부가 "필요"이다
    And 앱이 AAOS 주행 규제를 구독하고 있다
    When 플랫폼이 기어를 "<기어>"로 알리고 속도는 알리지 않는다
    Then AAOS 주행 규제 상태는 "<상태>"이다

    Examples:
      | 기어 | 상태    |
      | P  | 조작 가능 |
      | D  | 조작 제한 |

  Scenario Outline: 기어와 속도를 받을 수 없으면 주행 최적화 필요 여부로 판단한다
    Given Car 서비스가 주행 규제 값을 알려준다
    And 앱이 AAOS 주행 규제를 구독하고 있다
    When 플랫폼이 주행 최적화 필요 여부를 "<필요 여부>"로 알린다
    Then AAOS 주행 규제 상태는 "<상태>"이다

    Examples:
      | 필요 여부 | 상태    |
      | 필요 없음 | 조작 가능 |
      | 필요     | 조작 제한 |

  Scenario: 구독을 시작하면 현재 주행 규제 값을 바로 읽는다
    Given Car 서비스의 현재 주행 최적화 필요 여부가 "필요"이다
    When 앱이 AAOS 주행 규제를 구독한다
    Then AAOS 주행 규제 상태는 "조작 제한"이다

  Scenario Outline: 주행 규제 값을 확인할 수 없으면 확인 불가로 처리한다
    Given Car 서비스가 "<상황>"
    When 앱이 AAOS 주행 규제를 구독한다
    Then AAOS 주행 규제 상태는 "확인 불가"이다
    And 화면 조작을 허용하지 않는다

    Examples:
      | 상황         |
      | 연결되지 않는다   |
      | 첫 값을 주지 않는다 |
      | 예외를 던진다     |

  Scenario: 구독 중 Car 서비스 연결이 끊기면 확인 불가가 된다
    Given Car 서비스가 주행 규제 값을 알려준다
    And 앱이 AAOS 주행 규제를 구독하고 있다
    When Car 서비스 연결이 끊긴다
    Then AAOS 주행 규제 상태는 "확인 불가"이다

  Scenario: 구독자가 없으면 Car 서비스 연결을 끊는다
    Given Car 서비스가 주행 규제 값을 알려준다
    And 앱이 AAOS 주행 규제를 구독하고 있다
    When 앱이 AAOS 주행 규제 구독을 멈춘다
    Then Car 서비스 연결이 끊겨 있다
    And AAOS 주행 규제 상태는 "확인 불가"이다

  Scenario: Pleos 에뮬레이터의 Car 서비스에서 주행 규제 값을 받는다
    Given 실제 Car 서비스를 사용한다
    When 앱이 AAOS 주행 규제를 구독한다
    Then AAOS 주행 규제 상태는 "조작 가능" 또는 "조작 제한"이다

  Scenario: 디버그 화면에 현재 주행 규제 상태를 표시한다
    Given 테스트용 차량 연동이 주입되어 있다
    And 앱을 실행한다
    When 테스트에서 주행 규제 상태를 "조작 제한"로 바꾼다
    Then 화면에 "주행 규제 상태: 조작 제한"가 표시된다
