Feature: Firebase 익명 사용자
  로그인 입력 없이 익명 사용자를 만들고 닉네임을 부여한다.

  Issues:
  - [#73 feat: Firebase 익명 인증과 자동 닉네임 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/73)
  - [#126 feat: 프로필 화면과 닉네임 변경 추가](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/126)

  Scenario: 익명 인증에 성공한다
    Given 익명 인증이 가능하다
    When 익명 사용자 등록을 요청한다
    Then 사용자 식별자와 닉네임을 받는다

  Scenario: 인증에 실패하면 다시 시도한다
    Given 익명 인증이 일시적으로 실패한다
    When 익명 사용자 등록을 요청한다
    Then 인증 실패 상태가 된다
    When 익명 인증을 다시 시도한다
    Then 사용자 식별자와 닉네임을 받는다

  Scenario: 새 사용자에게 읽기 좋은 자동 닉네임을 붙인다
    When 새 사용자 "new-user-uid"의 자동 닉네임을 만든다
    Then 자동 닉네임은 "형용사 명사 숫자" 형식이고 닉네임 규칙을 지킨다
    And 같은 사용자의 자동 닉네임은 다시 만들어도 같다
