# Firebase 클라이언트 조회·프로필 계약

이 문서는 [개인 기록과 리더보드](firebase-client.md), [공개 작품과 투표](firebase-artwork-client.md)를 보완한다. 서버 함수는 사용하지 않는다. 모든 조회는 Firebase Authentication 인증이 필요하다. 익명 계정이 다른 UID로 바뀌면 이전 계정의 개인 기록·작품 소유권을 조회하거나 복구할 수 없다.

## 동그라미 리더보드

경로: `leaderboards/circle/entries`. 사용자별 문서 ID는 UID다. 정렬은 `score` 내림차순, `achievedAt` 오름차순, `FieldPath.documentId()` 오름차순이다. 첫 페이지는 이 순서로 `orderBy` 후 `limit(20)`으로 가져온다. 다음 페이지는 동일한 쿼리에 마지막 `DocumentSnapshot`으로 `startAfter`를 적용한다. 끝 페이지는 반환된 문서가 20개보다 적거나 다음 페이지가 비어 있을 때다. 20개는 화면에 맞게 바꿀 수 있다.

내 항목은 `entries/{현재 UID}`로 읽는다. 문서가 없으면 아직 등록된 점수가 없음을 안내한다. 내 순위는 서버 `count()`로 `score > 내 점수`인 문서 수를 구해 1을 더한다. **동점은 공동 순위**이며 같은 점수 내 표시 순서는 달성 시각과 UID로 정한다. 예: 100점 2명과 90점 1명은 순위 1, 1, 3이다. 이는 같은 점수에 서로 다른 순번을 부여하는 방식이 아니다.

앱의 순위 화면(`kr.roadcanvas.app.circleranking`)은 페이지를 넘기지 않고 첫 페이지를 `limit(10)`으로 읽어 상위 10명을 보여 준다. 같은 점수는 앞 사람과 같은 순위로 표시한다. 내 항목이 10위 안이면 그 순위를 쓰고, 밖이면 `entries/{현재 UID}`를 읽어 위 `count()`로 순위를 구해 순위표 아래에 따로 보여 준다. 정렬 인덱스(`score` 내림차순, `achievedAt` 오름차순, `__name__` 오름차순)는 운영 프로젝트에 배포되어 있음을 `firebase firestore:indexes`로 확인했다(2026-11-09). 읽기 전에 기기의 완료 기록 등록을 최대 10초 기다려 방금 끝낸 도전도 순위에 들어가게 한다.

순위 줄(내 줄 포함)을 누르면 기록 상세를 연다. `entries/{uid}`의 `drawingId`로 `users/{uid}/drawings/{drawingId}`와 `chunks`를 서버에서 읽고, 청크 `index`가 0부터 `pointChunkCount`-1까지 한 번씩 있는지 확인해 이어 붙인다. 빠지거나 형식이 맞지 않으면 경로를 불러오지 못했다고 안내한다. 점수 구성과 맞춤 원은 서버에 저장하지 않으므로 경로로 다시 계산하고, 점수는 순위 항목의 값을 보여 준다. 규칙은 순위 항목이 가리키는 그림 하나만 다른 사용자에게 공개하며 `firebase/territory-verification`의 `npm run test:rules`가 확인한다.

`count()`는 서버 집계다. 오프라인 또는 조회 실패 시 내 순위를 알 수 없다고 표시하고 마지막 값을 최신 결과인 것처럼 표시하지 않는다. 내 문서를 읽은 뒤 집계하기 전 다른 점수가 등록·변경될 수 있어 목록과 순위는 완전히 같은 시점의 원자적 스냅샷이 아니다. 페이지 조회 중에도 순서가 바뀌면 항목이 중복되거나 빠질 수 있으므로 새로고침을 제공한다. Firebase 집계는 읽은 인덱스 항목에 따라 비용과 지연이 늘 수 있다.

## 공개 작품과 내 작품

경로: `artworks`. 기본 공개 목록은 `whereEqualTo("visibility", "PUBLISHED")`, `orderBy("createdAt", DESCENDING)`, `limit(20)`으로 조회한다. 본인 작품은 `whereEqualTo("ownerUid", 현재 UID)`, 같은 정렬·제한을 사용한다. 후자는 본인의 `DRAFT`와 `PUBLISHED`를 모두 포함한다. 각 쿼리는 마지막 문서 스냅샷으로 `startAfter`해 다음 페이지를 불러온다. 같은 시각은 Firestore의 문서 ID 정렬을 사용하며 스냅샷 커서로 이어야 한다. 조회 중 작품 공개 상태나 생성 시각이 바뀔 수 있으므로 새로고침을 제공한다. 쿼리에 필터를 빼면 다른 사람의 초안 접근 때문에 규칙에서 거부될 수 있다.

정렬 인덱스는 `firebase/firestore.indexes.json`에 있다. 새 인덱스는 배포 직후 구축 중일 수 있으므로 실제 서버 질의가 성공하는지 확인한다. 빈 결과, 인증 실패, 네트워크 실패를 구분해 화면에 안내한다. 투표 수별 정렬은 현재 지원하지 않는다.

## 닉네임 변경

닉네임은 `users/{uid}.nickname`, `leaderboards/circle/entries/{uid}.nickname`, `territoryGridOwners/{uid}.nickname`(#148 이전은 `territoryOwners`), 작성한 각 `artworks/{id}.nickname`에 복사되어 있다. 규칙은 1~30자를 받지만 앱의 프로필 화면(#126)은 앞뒤 공백을 제거한 2~12자, 한글·영문·숫자·공백·`-`·`_`만 받는다. 새 사용자의 자동 닉네임은 "형용사 명사 숫자"(예: `빠른 고래 12`, UID로 정해져 같은 사용자는 같다)이고, 이미 프로필이 있는 사용자는 바꾸지 않는다. 기존 프로필의 `createdAt`은 유지한다.

앱(`kr.roadcanvas.app.profile.FirestoreProfileStore`)은 다음 순서로 바꾼다.

1. 리더보드·땅따먹기 순위 문서와 `ownerUid == 현재 UID`인 본인 작품(초안과 공개 작품)을 서버에서 읽는다. 오프라인이면 여기서 실패하므로 쓰기를 대기열에 남기지 않는다.
2. `users/{uid}`와 읽은 문서 중 있는 것의 `nickname`만 한 `WriteBatch`로 수정한다. 없는 순위 문서는 만들지 않는다. 한 배치이므로 하나라도 규칙에 거부되면 아무것도 바뀌지 않고, 사용자는 다시 저장해 재시도한다. 같은 값을 다시 설정해도 무방하다.
3. 성공하면 앱 실행 중 계정(`AccountRegistration`)의 닉네임을 바꿔 이후 작품 공개, 땅따먹기 획득, 동그라미 순위 등록이 새 닉네임을 쓴다. 다음 실행은 `users/{uid}`에서 닉네임을 읽는다.

리더보드 항목을 수정할 때 규칙은 연결된 본인 동그라미 기록의 존재와 점수·알고리즘 버전 일치를 다시 검사하므로, 그 기록이 없거나 불일치하면 배치 전체가 실패한다. 배치는 500건 제한이 있어 본인 작품이 약 500개를 넘으면 실패한다. 현재 규칙으로 모든 수정이 허용되어 규칙을 바꾸지 않았다. 사용자가 투표한 문서에는 닉네임이 없으므로 수정하지 않는다.

참고: [Firestore 집계 질의](https://firebase.google.com/docs/firestore/query-data/aggregation-queries), [Firestore 인덱스](https://firebase.google.com/docs/firestore/query-data/index-overview).
