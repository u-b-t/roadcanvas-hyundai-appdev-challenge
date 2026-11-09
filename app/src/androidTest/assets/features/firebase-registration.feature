Feature: Firebase 완료 그림 등록
  완료 그림은 기기에 보관하며 Firebase 등록이 실패하면 다시 시도한다.

  Issues:
  - [#74 feat: 완료 그림과 최고 점수 Firebase 등록](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/74)

  Scenario: 기기 기록을 Firebase에 등록한다
    Given Firebase에 등록할 기기 기록이 있다
    When 기록 등록을 실행한다
    Then 기기 기록이 Firebase에 등록된다

  Scenario: 등록 실패 후 다시 시도한다
    Given Firebase 등록이 한 번 실패한다
    When 기록 등록을 실행한다
    Then 등록에 실패해도 기기 기록이 남는다
    When 기록 등록을 다시 실행한다
    Then 기기 기록이 Firebase에 등록된다
