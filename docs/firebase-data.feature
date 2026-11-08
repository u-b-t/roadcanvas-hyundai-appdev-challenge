Feature: Firebase 그림과 리더보드 데이터 접근
  인증된 사용자는 자신의 그림을 보관하고 공개 리더보드를 조회한다.

  Issues:
  - [#66 feat: Firebase 기록 및 리더보드 데이터 기반 구성](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/66)

  Scenario: 자신의 프로필과 완성 기록을 저장하고 조회한다
    Given Firebase에 인증된 사용자가 있다
    When 자신의 프로필과 완성된 동그라미 그림을 저장한다
    Then 자신의 프로필과 그림을 조회할 수 있다

  Scenario: 다른 사용자의 그림을 조회하거나 수정할 수 없다
    Given Firebase에 인증된 사용자가 있다
    When 다른 사용자의 그림을 조회하거나 수정한다
    Then 요청이 거부된다

  Scenario: 리더보드 점수 범위를 벗어난 기록을 거부한다
    Given Firebase에 인증된 사용자가 있다
    When 자신의 리더보드에 101점을 저장한다
    Then 요청이 거부된다

  Scenario: 인증하지 않은 사용자는 데이터를 조회할 수 없다
    Given Firebase에 인증되지 않은 사용자가 있다
    When 공개 리더보드를 조회한다
    Then 요청이 거부된다
