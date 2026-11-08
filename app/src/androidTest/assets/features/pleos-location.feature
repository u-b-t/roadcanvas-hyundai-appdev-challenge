Feature: Pleos 차량 위치
  위치 구현으로 "pleos"를 고르면 Pleos FusedLocation SDK에서 차량 위치를 받는다.
  SDK 상태는 앱이 다루는 위치 상태로 바꿔 전달하고, 위치를 다 받으면 SDK를 해제한다.
  시나리오는 SDK를 대체 구현으로 바꿔 확인한다. 실제 SDK 동작은 에뮬레이터에서 따로 확인한다.

  Issues:
  - [#12 FusedLocation SDK 위치 구현 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/12)

  Scenario: 위치를 받는 동안 받은 위치를 전달하고 멈추면 SDK를 해제한다
    Given Pleos 위치 SDK가 지원되는 환경이다
    And Pleos 위치 권한이 허용되어 있다
    When Pleos 위치 수신을 시작한다
    And SDK가 위도 37.5665, 경도 126.978 위치를 보낸다
    Then Pleos 위치 상태는 "사용 가능"이다
    And 앱이 Pleos 위치 위도 37.5665, 경도 126.978을 받는다
    When Pleos 위치 수신을 멈춘다
    Then SDK 리스너를 해제한 뒤 SDK를 해제한다

  Scenario: 비어 있는 위치는 전달하지 않는다
    Given Pleos 위치 SDK가 지원되는 환경이다
    And Pleos 위치 권한이 허용되어 있다
    When Pleos 위치 수신을 시작한다
    And SDK가 비어 있는 위치를 보낸다
    And SDK가 위도 37.5, 경도 127.0 위치를 보낸다
    Then 앱이 받은 Pleos 위치는 위도 37.5, 경도 127.0 하나뿐이다

  Scenario: SDK를 지원하지 않는 환경이면 미지원으로 알린다
    Given Pleos 위치 SDK가 지원되지 않는 환경이다
    When Pleos 위치 수신을 시작한다
    Then Pleos 위치 상태는 "미지원"이다
    And SDK를 초기화하지 않는다

  Scenario: 위치 권한이 없으면 요청할 권한을 알린다
    Given Pleos 위치 SDK가 지원되는 환경이다
    And Pleos 위치 권한이 허용되지 않았다
    When Pleos 위치 수신을 시작한다
    Then Pleos 위치 상태는 "권한 필요"이다
    And Pleos 위치 상태의 요청할 권한에 "pleos.car.permission.FUSED_LOCATION"이 포함된다

  Scenario: SDK가 오류를 알리면 오류 상태로 바꾼다
    Given Pleos 위치 SDK가 지원되는 환경이다
    And Pleos 위치 권한이 허용되어 있다
    When Pleos 위치 수신을 시작한다
    And SDK가 "service disconnected" 오류를 알린다
    Then Pleos 위치 상태는 "오류"이다

  Scenario: 위치 접근이 차단되면 차단 안내를 띄울 수 있다
    Given Pleos 위치 SDK가 지원되는 환경이다
    And Pleos 위치 권한이 허용되어 있다
    When Pleos 위치 수신을 시작한다
    And SDK가 위치 접근 차단 오류를 알린다
    Then Pleos 위치 상태는 "접근 차단"이다
    When 위치 접근 차단 안내를 요청한다
    Then SDK에 위치 접근 차단 안내를 요청한다
