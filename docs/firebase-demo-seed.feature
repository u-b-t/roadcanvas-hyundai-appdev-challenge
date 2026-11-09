Feature: 데모 데이터 시드
  심사 때 공개 작품, 영토 지도와 순위 화면이 비어 보이지 않도록 운영 Firebase에 예시 데이터를 넣는다.
  firebase/demo-seed/seed.mjs로 넣고 unseed.mjs로 정리한다. Firestore·Auth 에뮬레이터와 운영 프로젝트에서 실행해 확인하며 Android Cucumber에는 연결하지 않는다.

  Issues:
  - [#107 chore: 데모용 예시 작품, 영토와 기록 데이터 시드](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/107)

  Scenario: 앱과 같은 클라이언트 권한으로 데모 데이터를 넣는다
    Given 서비스 계정 없이 google-services.json의 API 키만 있다
    When 시드 스크립트를 실행한다
    Then 익명 인증 데모 사용자 6명이 닉네임 프로필을 갖는다
    And 공개 자유그림 작품 7개가 각각 1표 이상을 받는다
    And 서울 6개 지역에 사용자별로 칸 수가 다른 영토가 생기고 한 사용자는 다른 사용자의 칸을 빼앗는다
    And 사용자마다 점수가 다른 동그라미 챌린지 기록과 순위 항목이 생긴다

  Scenario: 다시 실행해도 데이터가 늘지 않는다
    Given 시드를 이미 실행했고 상태 파일에 데모 사용자의 로그인 정보가 남아 있다
    When 시드 스크립트를 다시 실행한다
    Then 같은 UID로 로그인하고 고정 ID 문서는 그대로 두며 투표와 획득을 다시 만들지 않는다

  Scenario: 상태 파일 없이 다시 실행해도 같은 작품과 획득을 만들지 않는다
    Given 상태 파일을 잃어 새 익명 사용자로 로그인한다
    When 시드 스크립트를 실행한다
    Then 고정 ID의 작품 문서는 다른 사용자가 고칠 수 없어 실패하고 중복 작품을 만들지 않는다

  Scenario: 데모 데이터를 정리한다
    Given 시드를 실행했다
    When 정리 스크립트를 실행한다
    Then 작품은 비공개가 되고 투표, 동그라미 기록과 순위 항목, 프로필은 지워지며 영토 순위 칸 수는 0이 된다
    And 규칙상 지울 수 없는 영토 칸과 획득 기록은 남는다
    And 다시 시드하면 작품을 공개하고 남은 칸 수로 영토 순위를 맞춘다

  Scenario: 앱 화면에서 데모 데이터를 본다
    Given Firebase 설정을 넣은 debug 앱을 에뮬레이터에 설치했다
    When 공개 작품 화면과 영토 지도를 연다
    Then 데모 작품과 표 수, 서울시청 주변 영토 칸과 상위 순위가 보인다
