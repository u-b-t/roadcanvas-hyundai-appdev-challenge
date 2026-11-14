// 데모 데이터를 운영 Firestore에 넣는다. 서비스 계정 없이 앱과 같은 익명 인증 클라이언트로 규칙을 지키며 쓴다.
// 문서 ID는 고정하고 이미 있는 문서는 건너뛰므로 다시 실행해도 중복되지 않는다. docs/firebase-demo-seed.md 참고.
import {
  doc,
  getDoc,
  getDocs,
  collection,
  getCountFromServer,
  query,
  where,
  runTransaction,
  serverTimestamp,
  setDoc,
  setLogLevel,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';
import { demoUser, firebaseConfig, STATE_PATH } from './client.mjs';
import { ARTWORKS, CAPTURES, CIRCLES, USERS, artworkRoute, captureCells, circleRoute, regionOf } from './data.mjs';

const CHUNK_SIZE = 100;
// 앱의 CIRCLE_SCORE_ALGORITHM_VERSION
const CIRCLE_ALGORITHM_VERSION = '1';

function chunksOf(points) {
  const chunks = [];
  for (let i = 0; i < points.length; i += CHUNK_SIZE) {
    chunks.push({ index: chunks.length, pointsJson: JSON.stringify(points.slice(i, i + CHUNK_SIZE)) });
  }
  return chunks;
}

async function seedProfile(user) {
  const ref = doc(user.db, 'users', user.uid);
  const snapshot = await getDoc(ref);
  if (snapshot.exists() && snapshot.data().nickname === user.nickname) return 'kept';
  await setDoc(ref, { nickname: user.nickname, createdAt: snapshot.exists() ? snapshot.data().createdAt : serverTimestamp() });
  return snapshot.exists() ? 'updated' : 'created';
}

// 앱의 FirestoreArtworkStore 순서: 초안 생성 → 청크 → 공개
async function seedArtwork(user, artwork, seed) {
  const { db } = user;
  const ref = doc(db, 'artworks', artwork.id);
  const route = artworkRoute(artwork, seed);
  const chunks = chunksOf(route.points);
  // 규칙상 없는 작품 문서는 읽을 수 없으므로 앱처럼 먼저 초안으로 만들어 보고, 이미 있으면(수정 거부) 읽는다.
  let created = true;
  try {
    await setDoc(ref, {
      ownerUid: user.uid,
      nickname: user.nickname,
      title: artwork.title,
      visibility: 'DRAFT',
      createdAt: serverTimestamp(),
      pointChunkCount: chunks.length,
      distanceMeters: route.distanceMeters,
      durationMillis: route.durationMillis,
    });
  } catch (error) {
    if (error.code !== 'permission-denied') throw error;
    created = false;
    const snapshot = await getDoc(ref);
    if (snapshot.data().visibility === 'PUBLISHED') return 'kept';
  }
  for (const chunk of chunks) await setDoc(doc(ref, 'chunks', String(chunk.index)), chunk);
  const stored = await getDocs(collection(ref, 'chunks'));
  if (stored.size !== chunks.length) throw new Error(`${artwork.id}: 청크 ${stored.size}/${chunks.length}`);
  await updateDoc(ref, { visibility: 'PUBLISHED', pointChunkCount: chunks.length });
  return created ? 'created' : 'published';
}

async function seedVote(voter, artworkId) {
  const ref = doc(voter.db, 'artworks', artworkId, 'votes', voter.uid);
  if ((await getDoc(ref)).exists()) return false;
  await setDoc(ref, { createdAt: serverTimestamp() });
  return true;
}

// 앱의 FirestoreTerritoryStore.submit과 같은 트랜잭션
function submitCapture(user, captureId, cells) {
  const { db } = user;
  return runTransaction(db, async (tx) => {
    const captureRef = doc(db, 'territoryGridCaptures', captureId);
    const existing = await tx.get(captureRef);
    if (existing.exists()) {
      const data = existing.data();
      if (data.ownerUid !== user.uid) throw new Error(`${captureId}: 다른 사용자(${data.ownerUid})의 획득 ID입니다.`);
      return { duplicate: true, newCells: data.newCellCount, stolenCells: data.stolenCellCount, ownCells: data.ownCellCount };
    }
    const cellRefs = cells.map((id) => doc(db, 'territoryGridCells', id));
    const snapshots = [];
    for (const ref of cellRefs) snapshots.push(await tx.get(ref));
    const previous = snapshots.map((s) => (s.exists() ? s.data().ownerUid : null));
    const outcome = {
      duplicate: false,
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

// 앱의 FirestoreDrawingUploader 순서: 청크 → 그림 문서 → 순위(더 높은 점수만 갱신)
async function seedCircle(user, circle, seed) {
  const { db } = user;
  const ref = doc(db, 'users', user.uid, 'drawings', circle.drawingId);
  const route = circleRoute(circle, seed);
  const startedAt = route.points[0].recordedAtMillis;
  const finishedAt = route.points[route.points.length - 1].recordedAtMillis;
  let drawing = 'kept';
  if (!(await getDoc(ref)).exists()) {
    const chunks = chunksOf(route.points);
    for (const chunk of chunks) await setDoc(doc(ref, 'chunks', String(chunk.index)), chunk);
    await setDoc(ref, {
      mode: 'CIRCLE',
      startedAt: Timestamp.fromMillis(startedAt),
      finishedAt: Timestamp.fromMillis(finishedAt),
      distanceMeters: route.distanceMeters,
      durationMillis: route.durationMillis,
      pointChunkCount: chunks.length,
      score: circle.score,
      algorithmVersion: CIRCLE_ALGORITHM_VERSION,
    });
    drawing = 'created';
  }
  const entry = doc(db, 'leaderboards', 'circle', 'entries', user.uid);
  const ranked = await runTransaction(db, async (tx) => {
    const current = await tx.get(entry);
    const score = current.exists() ? current.data().score : null;
    if (score != null && circle.score <= score) return false;
    tx.set(entry, {
      nickname: user.nickname,
      score: circle.score,
      achievedAt: Timestamp.fromMillis(finishedAt),
      drawingId: circle.drawingId,
      algorithmVersion: CIRCLE_ALGORITHM_VERSION,
    });
    return true;
  });
  return { drawing, ranked };
}

// 이미 있는 작품을 만들어 보다 거부되는 쓰기를 SDK가 오류 로그로 남기므로 끈다. 실패는 예외로 받는다.
setLogLevel('silent');
const config = firebaseConfig();
console.log(`프로젝트 ${config.projectId}, 상태 파일 ${STATE_PATH}`);
const users = {};
for (const [index, spec] of USERS.entries()) {
  const user = await demoUser(config, spec.key, spec.nickname);
  users[spec.key] = user;
  console.log(`사용자 ${spec.key} ${user.uid} ${spec.nickname} 프로필 ${await seedProfile(user)} (${index + 1}/${USERS.length})`);
}

for (const [index, artwork] of ARTWORKS.entries()) {
  const result = await seedArtwork(users[artwork.owner], artwork, 100 + index);
  let votes = 0;
  for (const voter of artwork.voters) if (await seedVote(users[voter], artwork.id)) votes++;
  console.log(`작품 artworks/${artwork.id} "${artwork.title}" ${result}, 새 투표 ${votes}/${artwork.voters.length}`);
}

for (const capture of CAPTURES) {
  const cells = captureCells(capture);
  const outcome = await submitCapture(users[capture.owner], capture.id, cells);
  console.log(
    `영토 territoryGridCaptures/${capture.id} ${capture.owner} ${cells.length}칸 ${outcome.duplicate ? '이미 반영' : '반영'}` +
      ` (새 ${outcome.newCells}, 빼앗음 ${outcome.stolenCells}, 내 칸 ${outcome.ownCells})`,
  );
}

// 정리(unseed.mjs)는 순위 칸 수만 0으로 바꾸고 칸은 남기므로, 다시 넣을 때 실제로 가진 칸 수로 순위를 맞춘다.
for (const user of Object.values(users)) {
  const owned = (await getCountFromServer(query(collection(user.db, 'territoryGridCells'), where('ownerUid', '==', user.uid)))).data().count;
  const ref = doc(user.db, 'territoryGridOwners', user.uid);
  const current = await getDoc(ref);
  if (current.exists() && current.data().cellCount === owned && current.data().nickname === user.nickname) continue;
  await setDoc(ref, { nickname: user.nickname, cellCount: owned }, { merge: true });
  console.log(`영토 순위 territoryGridOwners/${user.uid} ${user.key} ${owned}칸으로 맞춤`);
}

for (const [index, circle] of CIRCLES.entries()) {
  const user = users[circle.owner];
  const result = await seedCircle(user, circle, 200 + index);
  console.log(
    `동그라미 users/${user.uid}/drawings/${circle.drawingId} ${circle.score}점 ${result.drawing}, 순위 ${result.ranked ? '갱신' : '유지'}`,
  );
}

console.log('완료');
process.exit(0);
