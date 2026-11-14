// 운영 규칙(firebase/firestore.rules)의 서버 땅따먹기 규칙과 동그라미 순위 기록 공개 범위를 Firebase Local Emulator Suite에서 시험한다.
// 실행: npm run test:rules (firebase emulators:exec가 demo- 프로젝트로 에뮬레이터를 띄운 뒤 이 파일을 실행한다).
// 획득 트랜잭션은 앱의 FirestoreTerritoryStore.submit과 같은 순서로 읽고 쓴다.
import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInAnonymously } from 'firebase/auth';
import {
  collection,
  connectFirestoreEmulator,
  doc,
  getDoc,
  getDocs,
  getFirestore,
  limit,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import { gridBlock as block, regionOf } from './grid.mjs';

const PROJECT_ID = 'demo-roadcanvas-rules';
if (!PROJECT_ID.startsWith('demo-')) throw new Error('에뮬레이터 전용 demo- 프로젝트만 사용한다');
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';

const checks = [];
function check(name, ok, detail = '') {
  checks.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
}

async function createUser(name, signIn = true) {
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: 'demo-api-key' }, name);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, { disableWarnings: true });
  const db = getFirestore(app);
  const [host, port] = FIRESTORE_HOST.split(':');
  connectFirestoreEmulator(db, host, Number(port));
  const uid = signIn ? (await signInAnonymously(auth)).user.uid : null;
  return { name, db, uid, nickname: `드라이버-${name}` };
}

async function denied(promise) {
  try {
    await promise;
    return false;
  } catch (error) {
    return error.code === 'permission-denied';
  }
}

// FirestoreTerritoryStore.submit과 같은 트랜잭션
function submit(user, captureId, cells) {
  const { db } = user;
  return runTransaction(db, async (tx) => {
    const captureRef = doc(db, 'territoryGridCaptures', captureId);
    const existing = await tx.get(captureRef);
    if (existing.exists()) {
      const data = existing.data();
      return { duplicate: true, newCells: data.newCellCount, stolenCells: data.stolenCellCount, ownCells: data.ownCellCount };
    }
    const cellRefs = cells.map((id) => doc(db, 'territoryGridCells', id));
    const snapshots = [];
    for (const ref of cellRefs) snapshots.push(await tx.get(ref));
    const previous = snapshots.map((s) => (s.exists() ? s.data().ownerUid : null));
    const outcome = {
      newCells: previous.filter((p) => p == null).length,
      stolenCells: previous.filter((p) => p != null && p !== user.uid).length,
      ownCells: previous.filter((p) => p === user.uid).length,
    };
    const stolenBy = {};
    for (const p of previous) if (p != null && p !== user.uid) stolenBy[p] = (stolenBy[p] ?? 0) + 1;
    const victims = {};
    for (const uid of Object.keys(stolenBy)) victims[uid] = await tx.get(doc(db, 'territoryGridOwners', uid));
    const ownerRef = doc(db, 'territoryGridOwners', user.uid);
    const ownerSnap = await tx.get(ownerRef);
    const ownerCount = ownerSnap.exists() ? ownerSnap.data().cellCount : 0;

    tx.set(captureRef, {
      ownerUid: user.uid,
      createdAt: serverTimestamp(),
      cells,
      cellCount: cells.length,
      newCellCount: outcome.newCells,
      stolenCellCount: outcome.stolenCells,
      ownCellCount: outcome.ownCells,
    });
    snapshots.forEach((snapshot, index) => {
      tx.set(cellRefs[index], {
        ownerUid: user.uid,
        captureId,
        capturedAt: serverTimestamp(),
        version: (snapshot.exists() ? snapshot.data().version : 0) + 1,
        region: regionOf(cells[index]),
      });
    });
    tx.set(ownerRef, { nickname: user.nickname, cellCount: ownerCount + outcome.newCells + outcome.stolenCells }, { merge: true });
    for (const [uid, snapshot] of Object.entries(victims)) {
      if (!snapshot.exists()) continue;
      const count = snapshot.data().cellCount;
      const next = Math.max(0, count - stolenBy[uid]);
      if (next === count) continue;
      tx.update(snapshot.ref, { cellCount: next, lastLossCaptureId: captureId });
    }
    return outcome;
  });
}

// 앱처럼 permission-denied(경합)는 같은 획득 ID로 5번까지 실행한다.
async function submitWithRetry(user, captureId, cells) {
  for (let attempt = 1; ; attempt++) {
    try {
      return { ok: true, attempt, outcome: await submit(user, captureId, cells) };
    } catch (error) {
      if (error.code !== 'permission-denied' || attempt >= 5) return { ok: false, attempt, error: error.code };
      await new Promise((resolve) => setTimeout(resolve, 50 * attempt + Math.random() * 100));
    }
  }
}

async function ownerCount(db, uid) {
  const snapshot = await getDoc(doc(db, 'territoryGridOwners', uid));
  return snapshot.exists() ? snapshot.data().cellCount : 0;
}

const a = await createUser('a');
const b = await createUser('b');
const c = await createUser('c');
const others = await Promise.all(['d', 'e', 'f', 'g'].map((n) => createUser(n)));
const anonymous = await createUser('anonymous', false);

// 정상 흐름
const aCells = block(0, 0, 1, 3);
const first = await submit(a, 'cap-a1', aCells);
check('새 칸 획득: 칸 분류와 칸 수', first.newCells === 3 && (await ownerCount(a.db, a.uid)) === 3, JSON.stringify(first));
const cell0 = (await getDoc(doc(a.db, 'territoryGridCells', aCells[0]))).data();
check('칸 문서: 소유자, 획득 ID, version 1, region', cell0.ownerUid === a.uid && cell0.captureId === 'cap-a1' && cell0.version === 1 && cell0.region === regionOf(aCells[0]));

const bCells = [...aCells.slice(0, 2), ...block(1, 0, 1, 1)];
const steal = await submit(b, 'cap-b1', bCells);
const aAfter = (await getDoc(doc(b.db, 'territoryGridOwners', a.uid))).data();
check('탈취: 빼앗은 칸 2, 새 칸 1', steal.stolenCells === 2 && steal.newCells === 1, JSON.stringify(steal));
check('탈취: 빼앗긴 사용자 칸 수 감소와 획득 ID 기록', aAfter.cellCount === 1 && aAfter.lastLossCaptureId === 'cap-b1' && aAfter.nickname === a.nickname);
check('탈취: 내 칸 수 증가', (await ownerCount(b.db, b.uid)) === 3);
check('탈취: 칸 version 2', (await getDoc(doc(b.db, 'territoryGridCells', aCells[0]))).data().version === 2);

const again = await submit(b, 'cap-b2', bCells.slice(0, 1));
check('내 칸 재획득: 이미 내 칸 1, 칸 수 그대로', again.ownCells === 1 && (await ownerCount(b.db, b.uid)) === 3);
check('내 칸 재획득: 획득 ID와 version 갱신', (await getDoc(doc(b.db, 'territoryGridCells', bCells[0]))).data().captureId === 'cap-b2');

const duplicate = await submit(b, 'cap-b1', bCells);
check('중복 제출: 이전 결과를 돌려주고 아무것도 쓰지 않음', duplicate.duplicate && duplicate.stolenCells === 2 && (await ownerCount(b.db, b.uid)) === 3);

// 순위와 지도 조회
const top = await getDocs(query(collection(c.db, 'territoryGridOwners'), orderBy('cellCount', 'desc'), limit(10)));
check('순위: 보유 칸 수 내림차순 조회', top.docs.map((d) => d.data().cellCount).join(',') === '3,1');
const regions = [...new Set([...aCells, ...bCells].map(regionOf))];
const mapCells = await getDocs(query(collection(c.db, 'territoryGridCells'), where('region', 'in', regions), limit(301)));
check('지도: 구역으로 칸 조회', mapCells.size === 4, `${mapCells.size}개`);
check('비로그인 사용자는 칸·순위를 읽지 못함', (await denied(getDocs(collection(anonymous.db, 'territoryGridCells')))) && (await denied(getDocs(collection(anonymous.db, 'territoryGridOwners')))));

// 거부해야 하는 요청
const freeCell = block(5, 5, 1, 1)[0];
function captureDoc(user, cells, extra = {}) {
  return { ownerUid: user.uid, createdAt: serverTimestamp(), cells, cellCount: cells.length, newCellCount: cells.length, stolenCellCount: 0, ownCellCount: 0, ...extra };
}
function cellDoc(user, cellId, captureId, version = 1) {
  return { ownerUid: user.uid, captureId, capturedAt: serverTimestamp(), version, region: regionOf(cellId) };
}
async function batchDenied(build) {
  const batch = writeBatch(c.db);
  build(batch);
  return denied(batch.commit());
}

check('401칸 획득 기록 거부', await denied(setDoc(doc(c.db, 'territoryGridCaptures', 'big'), captureDoc(c, block(10, 10, 20, 21)))));
check('400칸 획득 기록 허용', !(await denied(setDoc(doc(c.db, 'territoryGridCaptures', 'max'), captureDoc(c, block(10, 10, 20, 20))))));
check('칸 분류 합이 칸 수와 다른 획득 기록 거부', await denied(setDoc(doc(c.db, 'territoryGridCaptures', 'bad-sum'), captureDoc(c, [freeCell], { newCellCount: 2 }))));
check('남의 UID로 획득 기록 거부', await denied(setDoc(doc(c.db, 'territoryGridCaptures', 'fake-owner'), captureDoc(c, [freeCell], { ownerUid: a.uid }))));
check('획득 기록 수정 거부', await denied(updateDoc(doc(b.db, 'territoryGridCaptures', 'cap-b1'), { cellCount: 3 })));
check('획득 기록 없이 칸 쓰기 거부', await denied(setDoc(doc(c.db, 'territoryGridCells', freeCell), cellDoc(c, freeCell, 'none'))));
check('이전 획득 기록을 재사용한 칸 쓰기 거부', await denied(setDoc(doc(c.db, 'territoryGridCells', freeCell), cellDoc(c, freeCell, 'max'))));
check('획득 기록에 없는 칸 쓰기 거부', await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-missing'), captureDoc(c, [block(6, 6, 1, 1)[0]]));
  batch.set(doc(c.db, 'territoryGridCells', freeCell), cellDoc(c, freeCell, 'c-missing'));
}));
const gridCell = block(7, 7, 1, 1)[0];
check('같은 획득의 새 칸 쓰기 허용(아래 거부 항목의 기준)', !(await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-ok'), captureDoc(c, [gridCell]));
  batch.set(doc(c.db, 'territoryGridCells', gridCell), cellDoc(c, gridCell, 'c-ok'));
})));
const otherCell = block(8, 8, 1, 1)[0];
check('구역이 칸 ID와 다른 칸 쓰기 거부', await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-region'), captureDoc(c, [otherCell]));
  batch.set(doc(c.db, 'territoryGridCells', otherCell), { ...cellDoc(c, otherCell, 'c-region'), region: regionOf(block(-20, -20, 1, 1)[0]) });
}));
for (const [name, id, region] of [['geohash 칸 ID', 'wydm9qy', 'wydm9q'], ['앞에 0을 붙인 칸 ID', `0${otherCell}`, regionOf(otherCell)]]) {
  check(`${name} 칸 쓰기 거부`, await batchDenied((batch) => {
    batch.set(doc(c.db, 'territoryGridCaptures', `c-${name}`), captureDoc(c, [id]));
    batch.set(doc(c.db, 'territoryGridCells', id), { ...cellDoc(c, otherCell, `c-${name}`), region });
  }));
}
check('오래된 version의 칸 쓰기 거부', await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-stale'), captureDoc(c, [aCells[0]], { newCellCount: 0, stolenCellCount: 1 }));
  batch.set(doc(c.db, 'territoryGridCells', aCells[0]), cellDoc(c, aCells[0], 'c-stale', 2));
}));
check('다른 사용자 순위 문서 생성 거부', await denied(setDoc(doc(c.db, 'territoryGridOwners', 'nobody'), { nickname: 'x', cellCount: 1 })));
check('다른 사용자 칸 수 증가 거부', await denied(updateDoc(doc(c.db, 'territoryGridOwners', a.uid), { cellCount: 5 })));
check('획득 기록 없이 다른 사용자 칸 수 감소 거부', await denied(updateDoc(doc(c.db, 'territoryGridOwners', b.uid), { cellCount: 2, lastLossCaptureId: 'max' })));
check('빼앗은 칸 수보다 많이 감소 거부', await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-over'), captureDoc(c, [bCells[2]], { newCellCount: 0, stolenCellCount: 1 }));
  batch.set(doc(c.db, 'territoryGridCells', bCells[2]), cellDoc(c, bCells[2], 'c-over', 2));
  batch.update(doc(c.db, 'territoryGridOwners', b.uid), { cellCount: 1, lastLossCaptureId: 'c-over' });
}));
check('다른 사용자 닉네임 변경 거부', await batchDenied((batch) => {
  batch.set(doc(c.db, 'territoryGridCaptures', 'c-nick'), captureDoc(c, [bCells[2]], { newCellCount: 0, stolenCellCount: 1 }));
  batch.set(doc(c.db, 'territoryGridCells', bCells[2]), cellDoc(c, bCells[2], 'c-nick', 2));
  batch.update(doc(c.db, 'territoryGridOwners', b.uid), { cellCount: 2, lastLossCaptureId: 'c-nick', nickname: '바꿈' });
}));
check('음수 칸 수 거부', await denied(setDoc(doc(c.db, 'territoryGridOwners', c.uid), { nickname: c.nickname, cellCount: -1 })));
check('칸 삭제 거부', await denied(writeBatch(b.db).delete(doc(b.db, 'territoryGridCells', bCells[0])).commit()));

// 동시 탈취: 같은 9칸에 4명이 동시에 제출하고 앱 재실행(최대 5번)으로 모두 반영한다.
const contested = block(20, 20, 3, 3);
await submit(a, 'cap-a-base', contested);
const outcomes = await Promise.all(others.map((user, i) => submitWithRetry(user, `cap-race-${i}`, contested)));
const cellOwners = await Promise.all(contested.map(async (id) => (await getDoc(doc(a.db, 'territoryGridCells', id))).data()));
const winner = cellOwners[0].ownerUid;
const counts = await Promise.all([a, ...others].map((u) => ownerCount(a.db, u.uid)));
check('동시 탈취: 모든 제출이 재실행으로 반영', outcomes.every((o) => o.ok), JSON.stringify(outcomes.map((o) => o.ok ? o.attempt : o.error)));
check('동시 탈취: 모든 칸이 마지막 반영의 소유자', cellOwners.every((cell) => cell.ownerUid === winner && cell.version === 1 + others.length));
const aExpected = 1; // cap-a1 이후 남은 칸
check('동시 탈취: 순위 칸 수가 소유 칸과 일치', counts[0] === aExpected && others.every((u, i) => counts[i + 1] === (u.uid === winner ? 9 : 0)), counts.join(','));

// 최대 칸 수: 획득 기록 1개 + 칸 400개 + 순위 문서를 트랜잭션 하나로 쓴다.
const large = await submitWithRetry(others[0], 'cap-400', block(40, 40, 20, 20));
check('400칸 획득 트랜잭션 반영', large.ok && large.outcome.newCells === 400, JSON.stringify(large.ok ? large.outcome : large.error));

// 그림 기록 mode
const drawing = { mode: 'TERRITORY', startedAt: new Date(1000), finishedAt: new Date(2000), distanceMeters: 400, durationMillis: 60000, pointChunkCount: 1 };
check('TERRITORY 그림 기록 허용', !(await denied(setDoc(doc(a.db, 'users', a.uid, 'drawings', 't1'), drawing))));
check('점수가 있는 TERRITORY 그림 기록 거부', await denied(setDoc(doc(a.db, 'users', a.uid, 'drawings', 't2'), { ...drawing, score: 10, algorithmVersion: 'x' })));

// 동그라미 순위 기록 상세: 순위 항목이 가리키는 그림과 청크만 다른 사용자가 읽는다.
const circle = { mode: 'CIRCLE', startedAt: new Date(1000), finishedAt: new Date(2000), distanceMeters: 300, durationMillis: 60000, pointChunkCount: 1, score: 80, algorithmVersion: '1' };
const circleChunk = { index: 0, pointsJson: '[{"latitude":37.5,"longitude":127.0,"recordedAtMillis":1000}]' };
for (const id of ['c-best', 'c-other']) {
  await setDoc(doc(a.db, 'users', a.uid, 'drawings', id, 'chunks', '0'), circleChunk);
  await setDoc(doc(a.db, 'users', a.uid, 'drawings', id), circle);
}
await setDoc(doc(a.db, 'leaderboards', 'circle', 'entries', a.uid), { nickname: a.nickname, score: 80, achievedAt: new Date(2000), drawingId: 'c-best', algorithmVersion: '1' });
check('순위 기록 그림 다른 사용자 읽기 허용', !(await denied(getDoc(doc(b.db, 'users', a.uid, 'drawings', 'c-best')))));
check('순위 기록 청크 다른 사용자 목록 읽기 허용', !(await denied(getDocs(collection(b.db, 'users', a.uid, 'drawings', 'c-best', 'chunks')))));
check('순위에 없는 그림 다른 사용자 읽기 거부', await denied(getDoc(doc(b.db, 'users', a.uid, 'drawings', 'c-other'))));
check('순위에 없는 그림 청크 다른 사용자 읽기 거부', await denied(getDocs(collection(b.db, 'users', a.uid, 'drawings', 'c-other', 'chunks'))));
check('순위 항목이 없는 사용자 그림 읽기 거부', await denied(getDoc(doc(a.db, 'users', b.uid, 'drawings', 'c-best'))));
check('인증 없이 순위 기록 그림 읽기 거부', await denied(getDoc(doc(anonymous.db, 'users', a.uid, 'drawings', 'c-best'))));
check('순위 기록 그림 다른 사용자 수정 거부', await denied(setDoc(doc(b.db, 'users', a.uid, 'drawings', 'c-best'), circle)));
check('순위 기록 청크 다른 사용자 삭제 거부', await denied(writeBatch(b.db).delete(doc(b.db, 'users', a.uid, 'drawings', 'c-best', 'chunks', '0')).commit()));
check('다른 사용자 그림 목록 읽기 거부', await denied(getDocs(collection(b.db, 'users', a.uid, 'drawings'))));

const failed = checks.filter((c) => !c.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} 통과`);
process.exit(failed.length ? 1 : 0);
