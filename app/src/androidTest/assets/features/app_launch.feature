Feature: 앱 시작
  앱을 실행하면 RoadCanvas 시작 화면을 거쳐 모드 선택 화면으로 들어간다.

  Issues:
  - [#1 Pleos 에뮬레이터에서 기본 앱 실행](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/1)
  - [#10 앱 시작 및 스플래시 화면 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/10)

  Scenario: 앱 이름이 포함된 시작 화면을 2초 동안 표시한 뒤 모드 선택 화면으로 이동한다
    Given 앱을 실행한다
    Then 화면에 "RoadCanvas"가 표시된다
    When 1.9초가 지난다
    Then 화면에 "RoadCanvas"가 표시된다
    When 0.1초가 더 지난다
    Then 화면에 "모드 선택"이 표시된다

  Scenario: 앱 화면은 가로 방향으로 고정된다
    Given 앱을 실행한다
    Then 화면 방향이 가로로 고정되어 있다

  Scenario: 모드 선택 화면에서 뒤로 가기를 눌러도 시작 화면으로 돌아가지 않는다
    Given 시작 화면 표시 시간을 0.3초로 줄여 앱 화면을 연다
    And 화면에 "모드 선택"이 표시된다
    When 뒤로 가기를 누른다
    Then 시작 화면이 다시 표시되지 않는다
