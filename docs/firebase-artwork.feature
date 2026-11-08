Feature: Firebase 자유그림 공개와 투표
  인증된 사용자는 자신의 작품을 공개하고 작품마다 한 표를 남기거나 취소한다.

  Issues:
  - [#80 feat: 자유그림 공개와 투표 Firebase 기반 구성](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/80)

  Scenario: 이름과 경로를 준비한 작품을 공개한다
    Given 익명 인증된 작성자가 비공개 작품을 만들었다
    And 작성자가 경로 청크를 모두 저장했다
    When 작성자가 작품을 공개한다
    Then 인증된 사용자는 작품과 경로를 볼 수 있다

  Scenario: 작품마다 한 표를 남기고 취소한다
    Given 인증된 사용자가 공개 작품을 보고 있다
    When 사용자가 작품에 투표한다
    Then 같은 사용자로 두 번째 투표를 생성할 수 없다
    When 사용자가 자신의 투표를 취소한다
    Then 투표가 삭제된다

  Scenario: 다른 사용자의 작품이나 투표를 바꾸지 못한다
    Given 다른 사용자가 공개한 작품과 투표가 있다
    When 인증된 사용자가 남의 작품이나 투표를 수정한다
    Then 요청이 거부된다

  Scenario: 인증하지 않은 사용자는 작품을 볼 수 없다
    Given 인증되지 않은 사용자가 있다
    When 공개 작품을 읽는다
    Then 요청이 거부된다
