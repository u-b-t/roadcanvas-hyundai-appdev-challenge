Feature: 디자인 시스템
  앱 화면은 SDG 색상과 Pretendard 글꼴의 라이트 테마로 표시한다.
  차량 화면에 맞게 조작 요소는 큰 터치 영역을 갖고, 조작 버튼과 핵심 정보는 운전석에 가까운 화면 왼쪽에 둔다.

  Issues:
  - [#47 SDG 디자인 시스템 도입과 라이트 테마 구성](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/47)
  - [#48 모드 선택 화면을 커버 이미지 배경과 목록 오버레이로 개편](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/48)

  Scenario: 기기가 다크 모드여도 앱 화면은 밝은 배경으로 표시된다
    Given 기기가 다크 모드인 상태로 앱 테마로 감싼 화면을 연다
    Then 화면에 "테마 확인"이 표시된다
    And 화면 배경이 밝은 색이다

  Scenario: 차량용 버튼은 글자가 짧아도 최소 터치 크기 이상이다
    Given 글자가 "확인"인 차량용 버튼을 그린다
    Then "확인" 버튼의 높이는 최소 터치 크기 이상이다
    And "확인" 버튼의 너비는 최소 터치 크기 이상이다

  Scenario: 조작 영역은 화면 왼쪽, 내용 영역은 오른쪽에 놓인다
    Given 운전석 쪽 배치로 조작 영역과 내용 영역을 그린다
    Then 조작 영역이 내용 영역보다 왼쪽에 있다

  Scenario: 오른쪽에서 왼쪽으로 쓰는 언어 설정이어도 조작 영역은 왼쪽에 놓인다
    Given 오른쪽에서 왼쪽으로 쓰는 언어 설정에서 운전석 쪽 배치로 조작 영역과 내용 영역을 그린다
    Then 조작 영역이 내용 영역보다 왼쪽에 있다

  Scenario: 지도 위에 띄운 조작 영역도 화면 왼쪽에 놓인다
    Given 오른쪽에서 왼쪽으로 쓰는 언어 설정에서 지도 위 오버레이 배치로 조작 영역을 그린다
    Then 조작 영역이 화면 왼쪽 절반에 있다
