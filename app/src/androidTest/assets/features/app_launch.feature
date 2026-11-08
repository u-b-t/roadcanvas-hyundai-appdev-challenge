Feature: 앱 시작
  앱을 실행하면 RoadCanvas 시작 화면을 거쳐 모드 선택 화면으로 들어간다.
  시작 화면은 커버 이미지를 화면 전체에 깔고 이미지 왼쪽의 어두운 영역에 앱 이름을 표시한다.
  앱 창은 처음부터 커버의 어두운 색으로 채워 빈 회색 창이 보이지 않고, 시작 화면은 모드 선택 화면과 같은 커버 배경을 써서
  모드 선택 화면이 같은 배경 위에서 나타난다. 시작 화면은 2초를 넘지 않는 1.2초 동안 보여준다.

  Issues:
  - [#1 Pleos 에뮬레이터에서 기본 앱 실행](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/1)
  - [#10 앱 시작 및 스플래시 화면 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/10)
  - [#50 내 그림·스플래시·정차 안내 화면에 SDG 적용](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/50)
  - [#62 정차 안내와 스플래시를 커버 스타일로 통일하고 시작 깜빡임 제거](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/62)

  Scenario: 앱 이름이 포함된 시작 화면을 1.2초 동안 표시한 뒤 모드 선택 화면으로 이동한다
    Given 앱을 실행한다
    Then 화면에 "RoadCanvas"가 표시된다
    When 1.1초가 지난다
    Then 화면에 "RoadCanvas"가 표시된다
    When 0.1초가 더 지난다
    Then 화면에 "모드 선택"이 표시된다

  Scenario: 시작 화면은 커버 이미지를 화면 전체에 깔고 앱 이름을 왼쪽에 표시한다
    Given 앱을 실행한다
    Then 커버 이미지가 화면 전체에 표시된다
    And "RoadCanvas"가 화면 왼쪽 절반에 표시된다

  Scenario: 앱 창은 처음부터 커버의 어두운 색으로 채운다
    Given 앱을 실행한다
    Then 앱 창 배경이 커버의 어두운 색이다

  Scenario: 모드 선택 화면은 시작 화면의 커버 배경 위에서 나타난다
    Given 앱을 실행한다
    When 1.2초가 지난다
    Then 모드 선택 화면이 나타나는 동안 시작 화면의 커버 이미지가 화면 전체에 남아 있다

  Scenario: 앱 화면은 가로 방향으로 고정된다
    Given 앱을 실행한다
    Then 화면 방향이 가로로 고정되어 있다

  Scenario: 모드 선택 화면에서 뒤로 가기를 눌러도 시작 화면으로 돌아가지 않는다
    Given 시작 화면 표시 시간을 0.3초로 줄여 앱 화면을 연다
    And 화면에 "모드 선택"이 표시된다
    When 뒤로 가기를 누른다
    Then 시작 화면이 다시 표시되지 않는다
