Feature: UI 테스트 격리
  Pleos UI 테스트의 각 시나리오는 앞 시나리오가 남긴 화면, 기록 세션, 구독과 저장소 상태 없이 같은 초기 상태에서 시작한다.
  화면을 닫거나 시나리오가 끝나 태스크가 지워진 직후 다음 화면을 열어도 Pleos 런처가 앞으로 가져오는 화면에 가려지지 않는다.
  두 번째와 세 번째 시나리오는 순서대로 실행되어 앞 시나리오가 남긴 상태가 정리되는지 확인한다.

  Issues:
  - [#159 Pleos UI 전체 테스트의 화면 생명주기 실패와 기기 중단 조사](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/159)
  - [#160 Pleos GNSS 수집 중지 시 system_server 워치독 조사](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/160)
  - [#33 Pleos UI 테스트의 간헐적 Compose 화면 미발견 실패 해결](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/33)

  Scenario: 화면을 닫자마자 다시 열어도 새 화면이 가려지지 않는다
    When 테스트 화면을 닫자마자 다시 여는 것을 10번 반복한다
    Then 마지막에 연 테스트 화면이 1초 동안 재개된 상태로 표시된다

  Scenario: 화면과 기록 세션, 저장소 오류 설정을 남긴 채 시나리오가 끝난다
    Given 경로 점 2개를 기록한 진행 중인 도전이 저장되어 있다
    When 앱을 다시 실행한다
    And 이어서 기록이 시작될 때까지 기다린다
    And 테스트에서 주행 규제 상태를 "조작 제한"로 바꾼다
    And 진행 중 기록 저장이 실패하도록 설정되어 있다
    Then 시나리오 화면이 열려 있다

  Scenario: 앞 시나리오가 남긴 상태 없이 시작한다
    Then 시나리오 화면이 열려 있지 않다
    And 기록 세션이 실행 중이지 않고 위치와 주행 규제 구독이 없다
    And 테스트용 저장소가 비어 있고 실패 설정이 없다
    And 테스트용 위치는 사용 가능, 주행 규제는 조작 가능 상태다
