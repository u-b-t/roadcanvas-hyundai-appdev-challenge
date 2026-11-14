# 데모 데이터 시드

심사 때 공개 작품, 영토 지도와 순위 화면이 비어 보이지 않도록 운영 Firebase(`roadcanvas-f9f2a`)에 예시 데이터를 넣는다. 사용자가 운영 프로젝트 쓰기를 승인했다(#107). 시나리오는 [firebase-demo-seed.feature](firebase-demo-seed.feature)에 있다.

## 방식

- `firebase/demo-seed/`의 Node 스크립트가 Firebase JS SDK로 익명 로그인해 앱과 같은 순서로 쓴다. 서비스 계정이나 관리자 권한을 쓰지 않으므로 [운영 규칙](../firebase/firestore.rules)을 그대로 통과해야 한다.
  - 작품: 초안 생성 → 청크 → 공개(`FirestoreArtworkStore`), 투표는 `votes/{uid}` 생성
  - 영토: `FirestoreTerritoryStore.submit`과 같은 트랜잭션(획득 기록, 칸 version +1, 순위 칸 수, 빼앗긴 사용자 칸 수 감소)
  - 동그라미: 청크 → 그림 문서 → 순위 항목은 더 높은 점수만 갱신(`FirestoreDrawingUploader`)
- API 키와 프로젝트는 환경 변수 `FIREBASE_API_KEY`·`FIREBASE_PROJECT_ID` 또는 `app/google-services.json`(`GOOGLE_SERVICES_JSON`으로 경로 변경)에서 읽는다. 키는 커밋하지 않는다. `roadcanvas-f9f2a`가 아니면 실행을 멈춘다.
- 경로 모양은 서울 지명 근처에 하트·별·글자 등을 수식으로 그리고 12m 간격으로 나눠 GPS처럼 약간 흔든 것이다. 실제 도로를 따라 맞추지는 않았다. 시각은 2026-11-01부터 고정한 값이라 다시 실행해도 같다.

### 다시 실행

익명 계정은 로그아웃하면 다시 찾을 수 없다. 스크립트는 SDK의 로그인 정보(갱신 토큰 포함)를 저장소 밖 상태 파일 `~/.roadcanvas-demo-seed/state.json`(`DEMO_SEED_STATE`로 변경, 권한 600)에 저장하고, 다시 실행하면 같은 UID로 로그인한다. 상태 파일은 커밋하거나 공유하지 않는다. 데모 계정의 비밀번호와 같다.

모든 문서 ID가 고정이라 다시 실행하면 있는 문서는 건너뛴다. 투표는 본인 투표 문서가 있으면, 획득은 같은 획득 ID가 있으면 만들지 않는다. 마지막에 데모 사용자가 실제로 가진 칸 수로 `territoryGridOwners`를 맞춘다. 상태 파일을 잃으면 새 익명 사용자가 생기지만, 고정 ID 작품과 획득은 다른 사용자가 고칠 수 없어 실패하므로 중복 작품이나 중복 획득은 생기지 않는다.

```sh
cd firebase/demo-seed
npm ci
cp <메인 체크아웃>/app/google-services.json ../../app/   # 없을 때만. 커밋하지 않는다(.gitignore)
npm run seed
```

운영에 쓰기 전에는 에뮬레이터에서 먼저 시험할 수 있다. `firebase emulators:exec`가 설정하는 `FIRESTORE_EMULATOR_HOST`·`FIREBASE_AUTH_EMULATOR_HOST`가 있으면 에뮬레이터에 연결한다.

```sh
DEMO_SEED_STATE=/tmp/demo-seed-emu.json FIREBASE_PROJECT_ID=demo-roadcanvas-seed FIREBASE_API_KEY=demo-key \
DEMO_SEED_ALLOW_OTHER_PROJECT=1 JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
npx firebase-tools emulators:exec --config ../../firebase.json --project demo-roadcanvas-seed \
  --only firestore,auth "node seed.mjs && node unseed.mjs && node seed.mjs"
```

2026-11-09 맥북에서 에뮬레이터로 시드 → 재실행 → 정리 → 재시드를 확인한 뒤 운영에 시드하고 한 번 더 실행해 새로 만든 문서가 없는 것을 확인했다.

## 운영 실행 결과 (2026-11-09)

데모 사용자. 각자 `users/{uid}`에 닉네임 프로필이 있다.

| 키 | UID | 닉네임 | 영토 칸(geohash, #148 이전) | 동그라미 점수 |
| --- | --- | --- | --- | --- |
| gangnam | `183tMSeknUOVDURpj5RYN0Yc0kO2` | 강남드리프터 | 125 | 96 |
| yeouido | `d3iN00eWRhe1guQrcDLALipWXhB2` | 여의도산책러 | 122 | 87 |
| cityhall | `EExMY5CzytNUj2uM8kqIgOb9RTm1` | 시청앞단골 | 56 (66칸 중 10칸을 빼앗김) | 91 |
| jamsil | `dMdFy8wwUaWiRfv99H1ZP1p4FTg1` | 잠실한바퀴 | 72 | 72 |
| hangang | `d3pWDdaWk5SyS5MEuBQqwI947Kr2` | 한강라이더 | 54 (새 44, 빼앗음 10) | 79 |
| mapo | `j8UPD5E1JYO308fSoLt6fdEvZ9d2` | 마포드라이브 | 30 | 64 |

공개 작품 `artworks/{id}`. 청크는 `chunks/0..`, 투표는 `votes/{데모 사용자 UID}`다.

| 문서 ID | 제목 | 작성자 | 위치 | 표 |
| --- | --- | --- | --- | --- |
| `demo-artwork-heart` | 여의도 하트 | yeouido | 여의도공원 | 5 |
| `demo-artwork-star` | 강남에 뜬 별 | gangnam | 강남 | 4 |
| `demo-artwork-hi` | HI 서울 | cityhall | 서울시청 | 3 |
| `demo-artwork-crown` | 광화문 왕관 | hangang | 광화문 | 3 |
| `demo-artwork-fish` | 상암 물고기 | mapo | 상암 | 2 |
| `demo-artwork-spiral` | 잠실 소용돌이 | jamsil | 잠실 | 1 |
| `demo-artwork-lightning` | 성수 번개 | gangnam | 성수 | 1 |

땅따먹기 획득 `territoryGridCaptures/{id}`. 칸은 `territoryGridCells/{row}_{col}`이고 `captureId`로 찾는다. #148에서 정사각형 격자로 바꾸며 같은 획득 ID로 새 컬렉션에 다시 넣는다. 아래 칸 수는 2026-11-14 에뮬레이터 시드 결과다. 이전 geohash 칸(`territoryCaptures`, `territoryCells`)의 칸 수는 위 사용자 표와 같고, 그 문서는 운영에 남아 있지만 새 규칙에서는 앱이 읽지 않는다.

| 획득 ID | 사용자 | 지역 | 칸 |
| --- | --- | --- | --- |
| `demo-capture-cityhall` | cityhall | 서울시청 주변 1.3km x 1km | 88 (15칸을 빼앗김) |
| `demo-capture-gangnam` | gangnam | 강남역 주변 다각형 | 162 |
| `demo-capture-yeouido` | yeouido | 여의도 타원 | 157 |
| `demo-capture-jamsil` | jamsil | 잠실 원 | 94 |
| `demo-capture-mapo` | mapo | 홍대 주변 | 38 |
| `demo-capture-hangang` | hangang | 광화문·종로(시청 칸 15개를 빼앗음) | 72 |

동그라미 기록은 `users/{uid}/drawings/demo-circle-<키>`(청크 `chunks/0..1`)이고, 순위 항목은 `leaderboards/circle/entries/{uid}`다. 점수 계산 버전은 `1`이다. 점수는 앱이 계산한 값이 아니라 정해 넣은 값이며, 점수가 낮을수록 찌그러진 원 경로를 넣었다.

## 지우기

```sh
cd firebase/demo-seed
npm run unseed                       # 클라이언트 권한으로 지울 수 있는 만큼 지운다
npm run unseed -- --delete-accounts  # 마지막에 익명 계정까지 지운다
```

- 작품은 규칙상 삭제할 수 없어 비공개(`DRAFT`)로 바꾼다. 다른 사용자에게 더 이상 보이지 않는다.
- 데모 사용자의 투표, 동그라미 기록·청크·순위 항목, 프로필은 삭제한다.
- 영토 칸·획득 기록·순위 문서(`territoryGrid*`)는 규칙상 삭제할 수 없다. 순위 칸 수만 0으로 바꾸므로 지도에는 칸이 남고(다른 사용자가 빼앗을 수 있다) 순위 상위에서는 빠진다. 다시 `npm run seed`를 실행하면 작품을 공개하고 남은 칸 수로 순위를 맞춘다.
- `--delete-accounts`로 계정을 지우면 남은 작품·칸·순위 문서를 클라이언트로 다시 고칠 수 없다. 완전히 지우려면 Firebase 콘솔에서 위 문서 ID와 UID로 직접 지운다(관리자 권한 작업이므로 따로 확인 후 진행).

## 확인하지 못한 사항

- 동그라미 순위 화면은 #110, 기록 상세는 #123에서 만들었다. 데모 순위 항목은 `drawingId`로 `demo-circle-<키>` 그림과 청크를 가리키므로 상세에서 경로가 보이는 구조다. 앱 화면으로는 아직 확인하지 않았다.
- 실제 사용자 데이터와 섞인 순위 순서는 운영 데이터에 따라 바뀐다.
