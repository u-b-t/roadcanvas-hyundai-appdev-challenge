Feature: Firebase 클라이언트 목록 조회와 프로필 변경
  사용자는 공개 작품과 동그라미 순위를 조회하고 자신의 닉네임을 변경한다.

  Issues:
  - [#83 feat: 클라이언트 목록 조회와 프로필 계약 보완](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/83)

  Scenario: 공개 작품을 최신순으로 조회한다
    Given 공개 작품과 다른 사용자의 비공개 작품이 있다
    When 인증된 사용자가 공개 작품을 최신순으로 조회한다
    Then 공개 작품만 최신순으로 표시한다

  Scenario: 자기 작품은 초안도 조회한다
    Given 로그인 사용자의 공개 작품과 초안이 있다
    When 자기 작품을 최신순으로 조회한다
    Then 자기 공개 작품과 초안을 최신순으로 표시한다

  Scenario: 닉네임 변경이 중간에 실패한다
    Given 사용자의 프로필과 리더보드 및 공개 작품이 있다
    When 닉네임 반영 중 네트워크가 끊긴다
    Then 성공한 변경은 유지하고 남은 반영을 재시도한다

  Scenario: 동점 사용자의 순위를 조회한다
    Given 같은 점수를 가진 사용자가 둘 이상 있다
    When 내 점수보다 높은 점수의 사용자 수를 센다
    Then 그 수에 1을 더한 동일 순위를 표시한다
