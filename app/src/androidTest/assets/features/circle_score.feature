Feature: 동그라미 챌린지 점수
  완료한 주행 경로가 얼마나 동그란지 0~100점으로 확인할 수 있다.
  닫힌 원만 점수를 받는다. 끝점이 출발점에서 50m 안에 있고 맞춘 원의 중심을 330° 이상 돌아야 한다.

  Issues:
  - [#15 동그라미 챌린지 점수 계산 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/15)
  - [#129 동그라미 챌린지는 닫힌 원을 그렸을 때만 점수 인정](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/129)

  Scenario: 완전한 원은 크기와 관계없이 높은 점수를 받는다
    Given 반지름 20m인 원을 1바퀴 주행했다
    When 경로 점수를 계산한다
    Then 점수는 95점 이상이다
    When 반지름 200m인 원을 1바퀴 주행한 경로 점수를 계산한다
    Then 두 점수의 차이는 1점 이하다

  Scenario: 위치 잡음이 있는 원도 높은 점수를 받는다
    Given 반지름 20m인 원을 위치 잡음 2m로 1바퀴 주행했다
    When 경로 점수를 계산한다
    Then 점수는 85점 이상이다

  Scenario: 반원만 주행하면 한 바퀴를 돌지 않아 0점이다
    Given 반지름 20m인 반원을 주행했다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "한 바퀴 미완성"이다

  Scenario: 원을 3/4만 주행하면 0점이다
    Given 반지름 20m인 원을 0.75바퀴만큼 주행했다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "한 바퀴 미완성"이다

  Scenario: 끝점이 출발점에서 멀면 원이 닫히지 않아 0점이다
    Given 반지름 100m인 원을 돌다가 출발점 60m 앞에서 멈췄다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "닫히지 않음"이다

  Scenario: 원을 두 바퀴 돌아도 높은 점수를 받는다
    Given 반지름 20m인 원을 2바퀴만큼 주행했다
    When 경로 점수를 계산한다
    Then 점수는 95점 이상이다

  Scenario: 직선을 왕복하면 점수가 매우 낮다
    Given 100m 직선을 왕복했다
    When 경로 점수를 계산한다
    Then 점수는 20점 이하다

  Scenario: 반시계 방향 원은 시계 방향 원과 같은 점수를 받는다
    Given 반지름 20m인 원을 시계 방향으로 1바퀴 주행했다
    When 경로 점수를 계산한다
    And 반지름 20m인 원을 반시계 방향으로 1바퀴 주행한 경로 점수를 계산한다
    Then 두 점수는 같다

  Scenario: 이동하지 않으면 0점이다
    Given 한 자리에서 위치값 30개를 기록했다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "이동 없음"이다

  Scenario: 위치값이 부족하면 0점이다
    Given 위치값 3개만 기록했다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "위치값 부족"이다

  Scenario: 최소 크기보다 작은 원은 0점이다
    Given 반지름 10m인 원을 1바퀴 주행했다
    When 경로 점수를 계산한다
    Then 점수는 0점이고 사유는 "최소 크기 미달"이다
