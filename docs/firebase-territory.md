# 서버 땅따먹기

기기에서 차지한 영역(`TerritoryResult.Claimed`, [땅따먹기 판정](../AGENTS.md#땅따먹기-판정))을 정사각형 격자 칸으로 바꿔 Firestore에 제출하고, 다른 사용자와 칸을 나누고 빼앗는다. [소유권 모델 검증](firebase-territory-ownership.md)의 후보 A(geohash7 격자 + 획득 기록 + 칸별 version + 앱 재시도)를 #103에서 확정했다. 화면은 #104에서 만들었다. geohash7 칸은 서울에서 약 121m x 153m 직사각형이라 지도에서 가로로 길쭉해 보여 #148에서 정사각형 격자로 바꿨다. 획득 기록, 칸별 version, 재시도 방식은 그대로다.

## 격자

| 항목 | 값 | 코드 |
| --- | --- | --- |
| 칸 크기 | 위도 0.00108°, 경도 0.00136°. 경도 간격을 위도 37.5°의 cos로 보정해 서울에서 약 120m x 120m다. 서울에서 멀어질수록 동서 길이가 달라진다(부산 약 123m, 적도 약 151m) | `TERRITORY_CELL_LAT_STEP`, `TERRITORY_CELL_LON_STEP` |
| 칸 번호 | `row = floor((위도 + 90) / 0.00108)`, `col = floor((경도 + 180) / 0.00136)`. 서울시청(37.5665, 126.9780)은 `118117_225719`다 | `TerritoryGrid.cellOf` |
| 칸 ID | `"{row}_{col}"`. 앞에 0을 붙이지 않는다 | `TerritoryGridCell.id`, `TerritoryGrid.parse` |
| 구역 | 칸 8 x 8(서울 약 960m 정사각형). ID는 `"{row / 8}_{col / 8}"`(몫)이며 지도 범위 조회 단위다. 이전 geohash6 접두어(약 1.2km x 0.6km)를 대신한다 | `TERRITORY_REGION_CELLS`, `TerritoryGridCell.regionId`, `TerritoryGrid.regionsCovering` |
| 칸 면적 | 서울시청 위도 기준 약 14,395m². 칸 수 환산 면적에 쓴다 | `TERRITORY_CELL_AREA_SQUARE_METERS` |

- 칸 크기는 계산식 대신 소수 값으로 고정했다. 앱(Kotlin)과 시드·규칙 시험 스크립트(JS)가 같은 배정밀도 나눗셈으로 같은 칸 번호를 얻게 하려는 것이다. JS 구현은 `firebase/territory-verification/grid.mjs`와 `firebase/demo-seed/data.mjs`에 있다.
- 위도 간격 고정 + 경도 보정(지역 미터 격자)을 골랐다. 지구 전체에서 정확한 정사각형은 아니지만 서비스 지역(서울)에서 정사각형이고 칸 ID 계산이 단순하다. 위도마다 경도 간격을 바꾸는 방식은 칸 경계가 행마다 어긋나 지도에 계단처럼 보여 쓰지 않았다.

## 게임 규칙

| 항목 | 규칙 | 코드 |
| --- | --- | --- |
| 획득 단위 | 격자 칸(서울 기준 약 120m x 120m). 차지한 다각형 안에 중심이 들어오는 칸을 획득한다. 그런 칸이 없으면 다각형 무게중심이 속한 칸 1개 | `territoryCellsOf` |
| 최대 칸 수 | 한 번에 400칸(약 5.8km²). 넘으면 획득하지 않고 "영역이 너무 커요"라고 안내한다. 기기 기록은 남긴다 | `TERRITORY_MAX_CELLS_PER_CAPTURE`, `TerritoryCaptureState.TooLarge` |
| 충돌 | 서버에 나중에 커밋된 획득이 이긴다. 칸 version 규칙과 앱 재실행(최대 5번)으로 잃어버린 갱신을 막는다 | `TERRITORY_SUBMIT_MAX_ATTEMPTS` |
| 탈취 | 다른 사용자의 칸은 언제든 빼앗는다. 보호 시간은 없다. 자기 칸을 다시 획득하면 획득 시각과 획득 ID만 갱신한다 | |
| 오프라인 | 획득 ID(UUID)와 칸 목록을 기기 DB에 저장하고, 연결되면 같은 획득 ID로 다시 제출한다. 만든 지 24시간이 지나면 포기하고 "서버에 반영하지 못함"으로 표시한다 | `TERRITORY_SUBMIT_DEADLINE_MILLIS`, `TerritoryCaptureState.Expired` |
| 표시 | 칸 수와 환산 면적(칸 수 x 칸 평균 면적 약 14,395m², 서울시청 위도 기준). 결과는 새로 차지한 칸, 빼앗은 칸, 이미 내 칸으로 나눈다 | `territoryAreaOf`, `TerritoryCaptureOutcome` |
| 순위 | `territoryGridOwners/{uid}`(닉네임, 보유 칸 수)를 획득 트랜잭션에서 함께 갱신한다. 상위 10명을 보유 칸 수 내림차순으로 보여 준다 | `TERRITORY_RANKING_SIZE` |
| 지도 조회 | 화면 범위를 덮는 구역으로 칸을 읽는다. 실시간 구독 없이 진입·새로고침·지도 이동이 끝났을 때 읽는다. 한 번에 최대 300칸, 구역 60개(약 55km²)까지이며 넘으면 확대하라고 안내한다 | `TERRITORY_MAP_MAX_CELLS`, `TERRITORY_MAP_MAX_REGIONS` |
| 기기 판정 | 닫힘 허용 거리 50m, 최소 면적 5,000m²(#98의 잠정값을 확정) | `TerritoryCalculator` |

## 위조 대응을 하지 않는다

공모전 데모 범위라 Cloud Functions 같은 서버 검증을 두지 않는다. 서버는 주행 경로를 보지 않고 클라이언트가 보낸 칸 목록, 칸 분류 수, 순위 칸 수를 믿는다. 규칙은 문서 형태, 본인 UID, 같은 요청의 본인 획득 기록, 칸 version, 다른 사용자 칸 수를 늘리지 못하는 것만 검사한다. 따라서 수정한 클라이언트는 아무 칸이나 획득하거나 자기 순위 칸 수를 부풀리거나, 자기 획득의 빼앗은 칸 수 이하만큼 다른 사용자의 칸 수를 줄일 수 있다. 운영 서비스로 쓰려면 서버가 경로를 받아 칸을 계산하고 순위를 집계해야 한다.

## 데이터 계약

| 경로 | 필드 | 규칙 |
| --- | --- | --- |
| `territoryGridCaptures/{captureId}` | `ownerUid`, `createdAt`(서버 시각), `cells`(칸 ID 1~400개), `cellCount`, `newCellCount`, `stolenCellCount`, `ownCellCount` | 로그인 사용자 읽기. 본인 UID, `createdAt == request.time`, `cellCount == cells.size()`, 세 분류 수의 합이 `cellCount`일 때만 생성. 수정·삭제 불가 |
| `territoryGridCells/{row}_{col}` | `ownerUid`, `captureId`, `capturedAt`(서버 시각), `version`(생성 1, 이후 +1), `region`(구역 ID) | 로그인 사용자 읽기. 문서 ID가 칸 ID 형식(`^(0\|[1-9][0-9]{0,5})_(0\|[1-9][0-9]{0,5})$`)이고 `region`이 문서 ID에서 계산한 구역과 같으며, 본인 UID, 같은 요청에서 만든 본인 획득 기록(`getAfter`)에 이 칸이 포함, version +1일 때만 쓰기. 삭제 불가 |
| `territoryGridOwners/{uid}` | `nickname`(1~30자), `cellCount`(0 이상), `lastLossCaptureId`(선택) | 로그인 사용자 읽기. 본인 문서는 생성·수정. 다른 사용자 문서는 같은 요청에서 만든 본인 획득 기록을 `lastLossCaptureId`에 적고, `cellCount`를 그 획득의 `stolenCellCount` 이하만큼 줄일 때만 수정. 삭제 불가 |

- 칸 분류 수는 중복 제출에서 같은 결과를 돌려주려고 획득 기록에 남긴다. 클라이언트가 계산한 값이다.
- 그림 기록 `users/{uid}/drawings`의 `mode`에 `TERRITORY`를 추가했다. 점수와 알고리즘 버전은 넣지 않는다. 현재 완료 그림 등록은 동그라미만 올린다.
- 순위는 `cellCount` 단일 필드 내림차순, 지도는 `region` `in` 조회(30개씩 나눠 조회)라 복합 색인이 필요 없다.

## geohash 칸 데이터 처리(#148)

- 새 격자는 새 컬렉션 `territoryGridCaptures`·`territoryGridCells`·`territoryGridOwners`를 쓴다. geohash 칸 ID는 새 칸과 1:1로 대응하지 않아 옮기지 않았다. 같은 컬렉션을 쓰면 이전 칸의 순위 칸 수가 남아 순위가 어긋나고, 규칙상 클라이언트가 칸·획득 기록·순위 문서를 지울 수 없다.
- 운영 규칙에서 이전 `territoryCaptures`·`territoryCells`·`territoryOwners`를 뺐다. 이전 문서는 남지만 앱이 읽거나 쓸 수 없다. 지우려면 Firebase 콘솔이나 관리자 권한으로 따로 지운다.
- 이전 앱 설치본은 새 규칙에서 땅따먹기 제출·조회가 거부된다(연결 실패가 아니라 권한 오류).
- 기기에 남은 #148 이전 획득(칸이 geohash)은 반영 여부와 관계없이 새 격자에서 읽을 수 없으므로 획득을 만든 적이 없는 기록으로 본다(`TerritoryGame.state`가 null). 앱 실행 때 자동으로 다시 제출하지 않고, 결과 화면이나 내 그림 상세에서 제출하면 저장한 경로로 새 격자 칸 획득을 새 획득 ID로 만들어 기기의 이전 획득을 바꾼다.
- 데모 데이터는 같은 획득 ID로 새 컬렉션에 다시 시드한다([데모 데이터 시드](firebase-demo-seed.md)).
- 닉네임 변경 배치는 `territoryGridOwners/{uid}`를 고친다.

## 앱 구현

- 화면은 `AppContainer.territoryGame`(`kr.roadcanvas.app.territory.TerritoryGame`)만 쓴다. 결과는 예외 대신 `TerritoryCall.Failure(TerritoryFailure)`로 받는다.
  - `capture(drawingId, claimed)`: 칸 변환 → 획득을 Room `territory_capture`(DB 버전 6)에 `PENDING`으로 저장 → 제출. 같은 완료 기록은 처음 만든 획득 ID를 다시 쓴다. 기기에 저장하지 못하면 제출하지 않는다.
  - `state(drawingId)`, `resubmitPending()`, `mapCells(bounds)`, `myCellCount()`, `ranking()`.
- 서버 접근은 `TerritoryStore` 뒤에 둔다. Firebase 설정이 있으면 `FirestoreTerritoryStore`, 없으면 항상 연결 실패인 `UnavailableTerritoryStore`다. 테스트는 `app/src/sharedTest/`의 `FakeTerritoryStore`(연결 실패, 경합 거부 횟수, 응답만 잃은 제출 주입), `FakeTerritoryCaptureRepository`, `FakeTerritoryOwnerProvider`를 쓴다.
- 제출은 온라인 트랜잭션 하나다(오프라인 대기 batch를 쓰지 않는다). 획득 기록을 먼저 읽어 있으면 그 결과를 돌려주고, 없으면 칸·내 순위 문서·빼앗길 사용자 순위 문서를 모두 읽은 뒤 쓴다. 빼앗긴 사용자의 칸 수가 이미 0이면 쓰지 않는다.
- `PERMISSION_DENIED`와 `ABORTED`는 경합으로 보고 같은 획득 ID로 최대 5번 실행한다(대기 300ms x 시도 횟수). 검증에서 경합이 version 규칙 위반으로 나타났기 때문이다. 연결 실패·인증 실패는 바로 다시 실행하지 않고 `PENDING`으로 남긴다.
- 재제출 시점: 앱 실행(익명 인증 직후 `RoadCanvasApplication`), 결과 화면과 영토 화면 진입(#104).
- 완료 기록을 지워도 획득 기록은 지우지 않아 서버 반영을 이어간다.

## 규칙 테스트

운영 규칙 `firebase/firestore.rules`의 땅따먹기 부분은 Firestore·Auth 에뮬레이터(`demo-roadcanvas-rules` 프로젝트)에서 시험한다. 앱의 트랜잭션과 같은 순서로 읽고 쓰는 JS 구현으로 새 칸·탈취·재획득·중복 제출, 순위·구역 지도 조회, 거부해야 하는 요청(형식 위반, 칸 ID와 다른 구역, geohash·앞에 0을 붙인 칸 ID, 남의 UID, 획득 기록 없는 칸, 오래된 version, 다른 사용자 칸 수 증가·과다 감소·닉네임 변경 등), 4명 동시 탈취(앱 재실행 포함), 400칸 트랜잭션, `TERRITORY` 그림 기록을 확인한다.

```sh
cd firebase/territory-verification
npm ci
JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH npm run test:rules
```

루트 `firebase.json`(Firestore 8080, Auth 9099)을 쓴다. 2026-11-14 맥북에서 #148 격자로 47개 항목이 모두 통과했다. 기존 후보 비교 검증(`npm run verify`, `territory.rules`)은 그대로 남겼다.

## 배포

규칙만 배포한다. 다른 대상(인덱스, 인증 설정)은 함께 배포하지 않는다.

```sh
npx firebase-tools login:list   # 로그인 확인. 없으면 npx firebase-tools login
npx firebase-tools deploy --only firestore:rules --project roadcanvas-f9f2a
```

2026-11-09 #103 브랜치에서 위 명령으로 `roadcanvas-f9f2a`에 규칙을 배포했다(`released rules firebase/firestore.rules to cloud.firestore`). 운영 Firestore에 데이터를 쓰는 시험은 하지 않았다.

## 확인하지 못한 사항

- Android SDK 트랜잭션의 실제 동작(경합 시 오류 코드, 오프라인 실패 시간, 400칸 트랜잭션 처리 시간)은 운영·에뮬레이터 모두에서 시험하지 않았다. 규칙 테스트는 JS SDK로 했다.
- 운영 Firestore에서 400칸 트랜잭션이 규칙 접근 횟수나 요청 크기 제한에 걸리지 않는지는 에뮬레이터에서만 확인했다.
- 순위 칸 수는 서버 집계가 없어 클라이언트 오류나 위조로 실제 소유 칸과 어긋날 수 있다. 다시 맞추는 작업은 없다.
- 획득 기록 보관 기간과 삭제, 익명 계정 분실 시 소유 칸 처리는 정하지 않았다.
