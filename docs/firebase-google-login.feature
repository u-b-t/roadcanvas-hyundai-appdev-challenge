Feature: Firebase Google 로그인 준비
  사용자는 Google 계정으로 Firebase 사용자 식별을 할 수 있다.

  Issues:
  - [#71 feat: Firebase Google 로그인 제공자 설정](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/71)

  Scenario: Google 로그인 제공자를 사용할 수 있다
    Given Firebase Authentication에 Google 제공자가 활성화되어 있다
    When 등록된 Android 앱에서 Google 인증을 요청한다
    Then Firebase에 전달할 수 있는 Google 인증 정보를 얻는다

  Scenario: Google 로그인에 필요한 서비스가 없다
    Given 차량 환경에서 Google 인증을 지원하지 않는다
    When Google 로그인을 요청한다
    Then 로그인할 수 없는 이유를 안내한다
