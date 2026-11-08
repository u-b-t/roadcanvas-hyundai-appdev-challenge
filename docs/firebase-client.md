# Firebase 클라이언트 계약 초안

## 구성

`firebase.json`은 Firestore 규칙과 인덱스의 위치를 지정한다. 실제 규칙과 인덱스는 `firebase/`에 둔다. Android의 `app/google-services.json`은 각 환경에서 준비한다. 앱 Firebase SDK 연동은 별도 작업이다. Functions는 사용하지 않는다.

Firebase 프로젝트에 Firestore 데이터베이스와 Authentication을 생성·활성화해야 한다. 인증 방식은 후속 작업에서 결정하며, 익명 인증은 기기 삭제·변경 시 계정을 복구할 수 없으므로 계정 연결 전 백업으로 간주하지 않는다. 지금은 데이터베이스가 아직 생성되지 않았다. 설정 배포 전 프로젝트와 데이터베이스 위치를 확인한다.

## 문서 구조

모든 시각은 Firestore timestamp, 거리는 미터, 시간은 밀리초다. 모든 문서는 명시된 필드만 허용한다. 사용자는 자기 UID 아래 문서만 접근한다.

| 경로 | 필드 |
| --- | --- |
| `users/{uid}` | `nickname`(1~30자), `createdAt` |
| `users/{uid}/drawings/{id}` | `mode`(`CIRCLE` 또는 `FREE_DRAWING`), `startedAt`, `finishedAt`, `distanceMeters`, `durationMillis`, `pointChunkCount`. 동그라미만 `score`(정수 0~100), `algorithmVersion`(1~40자) 추가 |
| `users/{uid}/drawings/{id}/chunks/{chunkId}` | `index`(0 이상 정수), `pointsJson`(1~200,000자) |
| `leaderboards/circle/entries/{uid}` | `nickname`, `score`, `achievedAt`, `drawingId`, `algorithmVersion`. 인증된 사용자에게 공개 |

그림 ID는 기기에서 생성한 UUID를 재시도에도 유지한다. 경로는 순서대로 청크에 나누고 `index`로 정렬한다. `pointsJson`은 `[ {"latitude": 37.0, "longitude": 127.0, "recordedAtMillis": 1700000000000} ]` 형태의 JSON 문자열이다. 숫자는 유한한 유효 좌표이고 기록 시간은 Unix 밀리초여야 한다. 규칙은 문자열의 내부 JSON, 좌표 유효성, 청크 연속성이나 완성 여부를 검증하지 않으므로 클라이언트가 검증한다. 200,000자는 UTF-8 바이트 제한이 아니므로 클라이언트는 문서 크기 제한을 별도로 지켜야 한다.

Room에서 완료 기록을 보관하고 동일한 ID로 재시도한다. 업로드 실패 시 로컬 기록을 유지한다. 청크 업로드 후 그림 문서를 저장해 목록에 노출하고, 리더보드는 마지막에 갱신한다. 여러 쓰기는 원자적이지 않다. 중단된 업로드와 남은 청크를 후속 클라이언트 구현에서 재시도·정리해야 한다. Firestore에서 부모 문서를 지워도 하위 컬렉션은 삭제되지 않으므로 그림 삭제 시 청크를 직접 삭제한다.

## 리더보드

`entries`에서 `score` 내림차순, `achievedAt` 오름차순으로 조회하고 문서 ID를 마지막 동점 정렬 기준으로 사용한다. 사용자별 문서가 하나이므로 클라이언트는 트랜잭션에서 기존 점수를 읽고 더 높은 점수만 갱신한다. 동일 점수는 기존 기록을 유지한다. 삭제한 최고 기록의 재계산, 닉네임 변경 반영, 동기화 충돌은 클라이언트에서 처리해야 한다. 규칙은 점수의 진위, 최고 점수 여부, 그림과의 연결을 보장하지 않는다. 점수 위조 방지는 이번 범위 밖이다.

## 제한과 후속 작업

원본 경로는 소유자만 읽고, 리더보드에는 닉네임과 점수 등이 공개된다. Firestore는 보안 규칙을 필터로 적용하지 않는다. 개인 그림은 자신의 경로에서만 조회한다. 자유그림의 공개와 땅따먹기 영역 모델·동시 소유권 갱신은 아직 정의하지 않았다. 영역 연산을 기기에서 수행하는 경우 신뢰와 동시 충돌 문제를 따로 다뤄야 한다.

규칙과 인덱스는 Firebase CLI에서 `firebase emulators:exec --only firestore ...`로 로컬 검증하고, 실제 배포는 `firebase deploy --only firestore --project <프로젝트 ID>`로 수행한다. 배포는 원격 설정을 변경한다. 이 문서는 서버 측 점수 계산이나 위치 위조 검증을 약속하지 않는다.

검증 시나리오 초안은 `docs/firebase-data.feature`에 둔다. Firebase 전용 자동 테스트 코드는 작성하지 않기로 했으므로 Android Cucumber에 연결되어 있지 않으며 수동 검증 결과와 구분한다.
