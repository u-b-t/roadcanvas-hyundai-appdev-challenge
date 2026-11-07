Feature: 시작 화면
  앱을 실행하면 시작 문구를 확인할 수 있다.

  Issues:
  - [#1 Pleos 에뮬레이터에서 기본 앱 실행](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/1)

  Scenario: 시작 문구를 표시한다
    Given 앱을 실행한다
    Then 화면에 "Hello, RoadCanvas!"가 표시된다
