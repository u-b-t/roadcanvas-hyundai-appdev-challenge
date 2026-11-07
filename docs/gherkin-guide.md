# Gherkin 작성 참고

기능별 `.feature` 파일을 `app/src/androidTest/assets/features/` 아래에 둔다. 이 파일은 해당 기능이 현재 어떻게 동작해야 하는지 나타내는 명세이며, 이슈 하나마다 새 파일을 만들지 않는다.

## 예시

아래 이슈 번호와 시나리오는 형식을 설명하기 위한 예시다. 실제 작업에서는 해당 이슈의 제목과 주소, 확인된 요구사항으로 바꾼다.

```gherkin
Feature: 차량 정보 조회
  운전자가 차량 정보를 확인할 수 있다.

  Issues:
  - [#123 차량 정보 조회](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/123)
  - [#124 조회 실패 안내](https://github.com/u-b-t/roadcanvas-hyundai-appdev-challenge/issues/124)

  Scenario: 지원하는 차량 정보를 조회한다
    Given 차량 서비스가 연결되어 있다
    And 차량 정보 조회를 지원한다
    When 차량 정보를 요청한다
    Then 조회한 차량 정보가 화면에 표시된다

  Scenario: 차량 서비스에 연결할 수 없다
    Given 차량 서비스를 사용할 수 없다
    When 차량 정보를 요청한다
    Then 차량 정보를 조회할 수 없다는 안내가 표시된다
```

## 작성 기준

- 기능을 정의하거나 변경한 이슈는 `Feature` 설명의 `Issues:` 아래에 Markdown 링크로 한 번씩 기록한다. 이미 적힌 이슈는 중복 추가하지 않는다. 링크의 보고서 렌더링은 사용하는 리포터에 따라 다르다.
- `Feature`와 `Scenario`는 한국어로 기능과 상황을 설명한다. `Given`, `When`, `Then`, `And`는 영문 Gherkin 키워드를 사용한다.
- 시나리오는 사용자나 외부에서 확인할 수 있는 행동과 결과를 적는다. 내부 클래스명이나 구현 순서를 고정하지 않는다.
- 이슈의 완료 조건을 다루되 정상 흐름과 필요한 실패·미지원 상황을 구분한다. 실제로 확인되지 않은 동작을 사실처럼 작성하지 않는다.
- 시나리오 파일만으로 테스트 코드가 만들어지지 않는다. 각 단계에 대응하는 Cucumber 코드와 실행 결과를 확인한다. Pleos 에뮬레이터를 연결한 뒤 `python3 scripts/run-device-tests.py`로 실행한다. 유닛 테스트와 Pleos 에뮬레이터에서의 검증은 범위를 구분해 기록한다.
