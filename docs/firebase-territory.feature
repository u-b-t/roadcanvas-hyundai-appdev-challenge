Feature: 땅따먹기 격자 소유권 후보 검증
  여러 차량이 같은 칸을 동시에 획득하거나 같은 획득을 다시 제출해도 칸 소유 상태가 일관된다.
  Firebase Local Emulator Suite에서 firebase/territory-verification/verify.mjs로 확인하며 Android Cucumber에는 연결하지 않는다.

  Issues:
  - [#85 feat: Firebase 땅따먹기 소유권 모델 검증](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/85)

  Scenario: 여러 사용자가 겹치는 칸을 동시에 획득한다
    Given 다른 사용자가 소유한 칸이 있다
    When 여러 사용자가 그 칸과 겹치는 영역을 동시에 획득한다
    Then 모든 칸은 마지막으로 반영된 획득의 소유자를 가리킨다
    And 경합으로 거부된 획득은 다시 실행하면 반영된다

  Scenario: 응답을 잃은 획득을 같은 식별자로 다시 제출한다
    Given 사용자의 획득이 반영된 뒤 다른 사용자가 일부 칸을 빼앗았다
    When 같은 획득 식별자로 다시 제출한다
    Then 아무것도 바뀌지 않고 빼앗긴 칸은 다른 사용자의 소유로 남는다

  Scenario: 연결이 끊긴 동안 만든 획득을 재연결 후 제출한다
    Given 사용자의 연결이 끊겼다
    And 그동안 다른 사용자가 같은 칸을 빼앗았다
    When 재연결 후 끊긴 동안 대기한 오래된 쓰기가 전송된다
    Then 쓰기는 거부되고 다른 사용자의 소유가 유지된다
    And 같은 획득 식별자로 다시 실행하면 현재 소유자를 다시 읽어 반영한다

  Scenario: 정확한 도형을 색인 없이 동시에 획득한다
    Given 두 사용자가 아직 소유 도형이 없는 같은 지역을 조회했다
    When 두 사용자가 겹치는 도형을 각각 저장한다
    Then 두 도형이 겹친 채 저장되는 위험이 있다

  Scenario: 허용하지 않는 칸 쓰기를 거부한다
    Given Firebase에 인증된 사용자가 있다
    When 획득 기록 없이 칸을 쓰거나 다른 사용자 이름으로 획득 기록을 만든다
    Then 요청이 거부된다
