// 땅따먹기 소유권 모델을 Firebase Local Emulator Suite(Firestore, Auth)에서 실측한다.
// 실행: npm run verify (firebase emulators:exec가 demo- 프로젝트로 에뮬레이터를 띄운 뒤 이 파일을 실행한다).
import { deleteApp, initializeApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInAnonymously } from 'firebase/auth';
import {
  collection,
  connectFirestoreEmulator,
  disableNetwork,
  doc,
  enableNetwork,
  getCountFromServer,
  getDoc,
  getDocFromCache,
  getDocs,
  getFirestore,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  where,
  writeBatch,
} from 'firebase/firestore';
import net from 'node:net';
import polygonClipping from 'polygon-clipping';
import {
  block,
  boxAround,
  cellSizeMeters,
  coverBox,
  multiPolygonArea,
  multiPolygonBox,
  rectPolygon,
  vertexCount,
} from './geo.mjs';

const PROJECT_ID = 'demo-roadcanvas-territory';
if (!PROJECT_ID.startsWith('demo-')) throw new Error('에뮬레이터 전용 demo- 프로젝트만 사용한다');
const FIRESTORE_HOST = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8180';
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9199';
if (!process.env.FIRESTORE_EMULATOR_HOST) console.warn('FIRESTORE_EMULATOR_HOST가 없어 기본 에뮬레이터 주소를 쓴다');

const checks = [];
const results = {};

function check(name, ok, detail = '') {
  checks.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
}

async function clearFirestore() {
  const [host, port] = FIRESTORE_HOST.split(':');
  const response = await fetch(
    `http://${host}:${port}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  if (!response.ok) throw new Error(`에뮬레이터 초기화 실패: ${response.status}`);
}

async function createUser(name, signIn = true, firestoreHost = FIRESTORE_HOST) {
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: 'demo-api-key' }, name);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${AUTH_HOST}`, { disableWarnings: true });
  const db = getFirestore(app);
  const [host, port] = firestoreHost.split(':');
  connectFirestoreEmulator(db, host, Number(port));
  const uid = signIn ? (await signInAnonymously(auth)).user.uid : null;
  return { name, app, db, uid };
}

async function denied(promise) {
  try {
    await promise;
    return false;
  } catch (error) {
    return error.code === 'permission-denied';
  }
}

function errorCode(error) {
  return error?.code ?? error?.message ?? String(error);
}

function withTimeout(promise, millis) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error(`timeout ${millis}ms`)), millis)),
  ]);
}

// SDK는 permission-denied를 재시도하지 않는다. version 규칙이 경합을 거부로 바꾸므로 앱이 몇 번 더 실행해 본다.
// 규칙 위반이 실제 원인이면 매번 거부되므로 횟수를 제한한다.
async function retryOnDenied(run, limit = 5) {
  let outcome;
  for (let round = 1; round <= limit; round++) {
    outcome = await run();
    outcome.rounds = round;
    if (outcome.ok || outcome.error !== 'permission-denied') return outcome;
    await new Promise((resolve) => setTimeout(resolve, 50 * round + Math.random() * 100));
  }
  return outcome;
}

function summarize(outcomes) {
  const failed = outcomes.filter((o) => !o.ok);
  return {
    writers: outcomes.length,
    succeeded: outcomes.length - failed.length,
    failureCodes: [...new Set(failed.map((o) => o.error))],
    sdkAttempts: outcomes.map((o) => o.attempts),
    appRounds: outcomes.map((o) => o.rounds ?? 1),
  };
}

// 앞에 둔 TCP 중계를 끊어 실제 연결 끊김을 만든다. disableNetwork는 트랜잭션 RPC를 막지 않는다.
async function createProxy(target) {
  const [targetHost, targetPort] = target.split(':');
  const sockets = new Set();
  let online = true;
  const server = net.createServer((client) => {
    if (!online) return client.destroy();
    const upstream = net.connect(Number(targetPort), targetHost);
    for (const socket of [client, upstream]) {
      sockets.add(socket);
      socket.on('close', () => sockets.delete(socket));
      socket.on('error', () => {
        client.destroy();
        upstream.destroy();
      });
    }
    client.pipe(upstream).pipe(client);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    host: `127.0.0.1:${server.address().port}`,
    setOnline(value) {
      online = value;
      if (!value) for (const socket of sockets) socket.destroy();
    },
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

// ---------------------------------------------------------------- 격자 모델

// 획득 ID가 이미 있으면 아무것도 쓰지 않는다. 칸을 모두 트랜잭션에서 다시 읽어 현재 소유자를 덮어쓴다.
async function captureCells(user, captureId, cells, { maxAttempts = 5, cellCollection = 'territoryCells' } = {}) {
  let attempts = 0;
  const started = Date.now();
  try {
    const outcome = await runTransaction(
      user.db,
      async (tx) => {
        attempts++;
        const captureRef = doc(user.db, 'territoryCaptures', captureId);
        if ((await tx.get(captureRef)).exists()) return { duplicate: true, stolen: 0 };
        const snapshots = await Promise.all(cells.map((cell) => tx.get(doc(user.db, cellCollection, cell))));
        let stolen = 0;
        tx.set(captureRef, { ownerUid: user.uid, createdAt: serverTimestamp(), cells, cellCount: cells.length });
        snapshots.forEach((snapshot, index) => {
          const previous = snapshot.exists() ? snapshot.data() : null;
          if (previous && previous.ownerUid !== user.uid) stolen++;
          tx.set(snapshot.ref, {
            ownerUid: user.uid,
            captureId,
            capturedAt: serverTimestamp(),
            version: (previous?.version ?? 0) + 1,
            gh6: cells[index].slice(0, 6),
          });
        });
        return { duplicate: false, stolen };
      },
      { maxAttempts },
    );
    return { ok: true, attempts, millis: Date.now() - started, ...outcome };
  } catch (error) {
    return { ok: false, attempts, millis: Date.now() - started, error: errorCode(error) };
  }
}

// 모든 칸이 자신을 포함한 가장 늦은 획득을 가리키고 version이 그 칸을 포함한 획득 수와 같은지 확인한다.
async function gridInvariant(reader, cellCollection = 'territoryCells') {
  const [cellSnapshot, captureSnapshot] = await Promise.all([
    getDocs(collection(reader.db, cellCollection)),
    getDocs(collection(reader.db, 'territoryCaptures')),
  ]);
  const captures = new Map(captureSnapshot.docs.map((d) => [d.id, d.data()]));
  const problems = [];
  for (const cell of cellSnapshot.docs) {
    const data = cell.data();
    const covering = [...captures.entries()]
      .filter(([, capture]) => capture.cells.includes(cell.id))
      .sort((a, b) => a[1].createdAt.toMillis() - b[1].createdAt.toMillis());
    const latest = covering.at(-1);
    if (!latest || latest[0] !== data.captureId) problems.push(`${cell.id}: 최신 획득 아님`);
    if (latest && latest[1].ownerUid !== data.ownerUid) problems.push(`${cell.id}: 소유자 불일치`);
    if (data.version !== covering.length) problems.push(`${cell.id}: version ${data.version} != ${covering.length}`);
  }
  return { cells: cellSnapshot.size, captures: captureSnapshot.size, problems };
}

async function cellOwners(reader, cells) {
  const snapshots = await Promise.all(cells.map((cell) => getDoc(doc(reader.db, 'territoryCells', cell))));
  return snapshots.map((snapshot) => snapshot.data()?.ownerUid ?? null);
}

async function gridConcurrentSteal(users) {
  console.log('\n[G1] 격자: 동시 탈취');
  const [a, ...others] = users;
  // 다섯 사용자가 A의 5x5 영역과 서로의 영역에 겹치는 5x5 칸을 동시에 세 번씩 제출한다.
  const variants = [
    { key: 'versionRuleSdkRetry', cellCollection: 'territoryCells', appRetry: false },
    { key: 'noVersionRuleSdkRetry', cellCollection: 'territoryCellsNoVersion', appRetry: false },
    { key: 'versionRuleAppRetry', cellCollection: 'territoryCells', appRetry: true },
  ];
  results.gridConcurrent = {};
  for (const variant of variants) {
    await clearFirestore();
    const options = { cellCollection: variant.cellCollection };
    const base = await captureCells(a, `g1-${variant.key}-a`, block(0, 0, 5, 5), options);
    if (!base.ok) throw new Error(`A 획득 실패 ${base.error}`);
    const outcomes = [];
    for (let round = 0; round < 3; round++) {
      outcomes.push(
        ...(await Promise.all(
          others.map((user, index) => {
            const run = () =>
              captureCells(user, `g1-${variant.key}-${round}-${user.name}`, block(index - 2, index - 2, 5, 5), options);
            return variant.appRetry ? retryOnDenied(run) : run();
          }),
        )),
      );
    }
    const summary = summarize(outcomes);
    results.gridConcurrent[variant.key] = summary;
    console.log(`   ${variant.key}`, JSON.stringify(summary));
    const invariant = await gridInvariant(a, variant.cellCollection);
    check(
      `${variant.key}: 동시 획득 후 모든 칸이 마지막 획득을 가리키고 version이 획득 수와 같다`,
      invariant.problems.length === 0 && invariant.captures === 1 + summary.succeeded,
      `성공 ${summary.succeeded}/${summary.writers}, 획득 기록 ${invariant.captures}, 문제 ${invariant.problems.length}`,
    );
  }
  check(
    'version 규칙이 있으면 경합한 트랜잭션 일부가 SDK 재시도 없이 permission-denied로 끝난다',
    results.gridConcurrent.versionRuleSdkRetry.failureCodes.includes('permission-denied'),
  );
  check(
    'version 규칙을 빼면 같은 경합이 SDK 재시도로 모두 성공한다',
    results.gridConcurrent.noVersionRuleSdkRetry.succeeded === results.gridConcurrent.noVersionRuleSdkRetry.writers,
  );
  check(
    'version 규칙을 두고 앱이 permission-denied를 제한 횟수만큼 다시 실행하면 모두 성공한다',
    results.gridConcurrent.versionRuleAppRetry.succeeded === results.gridConcurrent.versionRuleAppRetry.writers,
  );

  // 여섯 사용자가 같은 9칸을 동시에 제출한다.
  await clearFirestore();
  const hot = block(20, 20, 3, 3);
  const outcomes = await Promise.all(
    users.map((user) => retryOnDenied(() => captureCells(user, `g1-hot-${user.name}`, hot), 10)),
  );
  results.gridHotContention = summarize(outcomes);
  console.log('   hot', JSON.stringify(results.gridHotContention));
  const hotInvariant = await gridInvariant(a);
  check(
    `같은 9칸을 ${users.length}명이 동시에 제출해도 앱 재시도로 모두 반영되고 상태가 일관된다`,
    hotInvariant.problems.length === 0 && results.gridHotContention.succeeded === users.length,
    `성공 ${results.gridHotContention.succeeded}/${users.length}`,
  );
}

async function gridDuplicateSubmission([a, b, c], anonymous) {
  console.log('\n[G2] 격자: 중복 제출');
  await clearFirestore();
  const area = block(0, 0, 4, 4);
  const first = await captureCells(b, 'g2-b', area);
  check('B가 16칸을 획득한다', first.ok && !first.duplicate);
  const steal = await captureCells(c, 'g2-c', block(2, 0, 2, 4));
  check('C가 그중 8칸을 빼앗는다', steal.ok && steal.stolen === 8, `빼앗은 칸 ${steal.stolen}`);

  // 응답을 잃은 B가 같은 획득 ID로 다시 제출한다.
  const retry = await captureCells(b, 'g2-b', area);
  const owners = await cellOwners(a, area);
  check(
    '같은 획득 ID 재시도는 아무것도 쓰지 않고 C가 빼앗은 칸을 되돌리지 않는다',
    retry.ok && retry.duplicate && owners.filter((owner) => owner === c.uid).length === 8,
  );

  // 트랜잭션 없이 이전 batch를 그대로 다시 보내면 생성 전용 획득 기록 때문에 전체가 거부된다.
  const blind = writeBatch(b.db);
  blind.set(doc(b.db, 'territoryCaptures', 'g2-b'), {
    ownerUid: b.uid,
    createdAt: serverTimestamp(),
    cells: area,
    cellCount: area.length,
  });
  area.forEach((cell) =>
    blind.set(doc(b.db, 'territoryCells', cell), {
      ownerUid: b.uid,
      captureId: 'g2-b',
      capturedAt: serverTimestamp(),
      version: 1,
      gh6: cell.slice(0, 6),
    }),
  );
  check('같은 획득 기록을 batch로 다시 만들면 거부된다', await denied(blind.commit()));

  const reuse = writeBatch(b.db);
  reuse.set(doc(b.db, 'territoryCells', area[15]), {
    ownerUid: b.uid,
    captureId: 'g2-b',
    capturedAt: serverTimestamp(),
    version: 3,
    gh6: area[15].slice(0, 6),
  });
  check('이전 획득 기록을 재사용해 칸만 다시 쓰면 거부된다', await denied(reuse.commit()));

  const forged = writeBatch(b.db);
  forged.set(doc(b.db, 'territoryCaptures', 'g2-forged'), {
    ownerUid: c.uid,
    createdAt: serverTimestamp(),
    cells: [area[0]],
    cellCount: 1,
  });
  check('다른 사용자 이름의 획득 기록은 거부된다', await denied(forged.commit()));

  const orphan = writeBatch(b.db);
  const outside = block(10, 10, 1, 1)[0];
  orphan.set(doc(b.db, 'territoryCells', outside), {
    ownerUid: b.uid,
    captureId: 'g2-missing',
    capturedAt: serverTimestamp(),
    version: 1,
    gh6: outside.slice(0, 6),
  });
  check('획득 기록 없이 칸만 쓰면 거부된다', await denied(orphan.commit()));

  const mismatch = await captureCells(b, 'g2-mismatch', area.slice(0, 2));
  const notListed = writeBatch(b.db);
  notListed.set(doc(b.db, 'territoryCaptures', 'g2-partial'), {
    ownerUid: b.uid,
    createdAt: serverTimestamp(),
    cells: [area[0]],
    cellCount: 1,
  });
  notListed.set(doc(b.db, 'territoryCells', area[1]), {
    ownerUid: b.uid,
    captureId: 'g2-partial',
    capturedAt: serverTimestamp(),
    version: 3,
    gh6: area[1].slice(0, 6),
  });
  check('획득 기록에 없는 칸을 함께 쓰면 거부된다', mismatch.ok && (await denied(notListed.commit())));

  check(
    '인증하지 않은 사용자는 칸을 읽을 수 없다',
    await denied(getDocs(collection(anonymous.db, 'territoryCells'))),
  );
  const invariant = await gridInvariant(a);
  check('중복 제출 시험 후에도 칸 상태가 일관된다', invariant.problems.length === 0, invariant.problems.join(', '));
}

async function gridOfflineRetry([a, , c], { user: b, proxy }) {
  console.log('\n[G3] 격자: 오프라인 재시도 (B는 TCP 중계를 거쳐 접속하고 중계를 끊어 연결을 잃는다)');
  await clearFirestore();
  const area = block(0, 0, 3, 3);
  const seeded = await captureCells(a, 'g3-a', area);
  check('A가 9칸을 획득한다', seeded.ok);
  // B가 온라인일 때 칸을 읽어 캐시에 둔다.
  await Promise.all(area.map((cell) => getDoc(doc(b.db, 'territoryCells', cell))));

  proxy.setOnline(false);
  const offlineTx = await withTimeout(captureCells(b, 'g3-b-tx', area), 120_000).catch((error) => ({
    ok: false,
    error: errorCode(error),
  }));
  results.offlineTransaction = offlineTx;
  check(
    '연결이 끊기면 트랜잭션은 재시도 후 실패하고 아무것도 쓰지 않는다',
    !offlineTx.ok && !(await getDoc(doc(a.db, 'territoryCaptures', 'g3-b-tx'))).exists(),
    `${offlineTx.error}, SDK 시도 ${offlineTx.attempts}회, ${offlineTx.millis}ms`,
  );

  // 연결이 끊긴 상태에서 캐시의 version으로 batch를 만들면 SDK가 대기열에 넣는다.
  const cached = await Promise.all(area.map((cell) => getDocFromCache(doc(b.db, 'territoryCells', cell))));
  const queued = writeBatch(b.db);
  queued.set(doc(b.db, 'territoryCaptures', 'g3-b'), {
    ownerUid: b.uid,
    createdAt: serverTimestamp(),
    cells: area,
    cellCount: area.length,
  });
  cached.forEach((snapshot, index) =>
    queued.set(snapshot.ref, {
      ownerUid: b.uid,
      captureId: 'g3-b',
      capturedAt: serverTimestamp(),
      version: snapshot.data().version + 1,
      gh6: area[index].slice(0, 6),
    }),
  );
  let queuedSettled = null;
  const queuedCommit = queued.commit().then(
    () => (queuedSettled = 'committed'),
    (error) => (queuedSettled = errorCode(error)),
  );

  // B가 끊긴 동안 C가 같은 칸을 빼앗는다. 비교용 laxCells도 C가 먼저 쓴다.
  await setDoc(doc(c.db, 'laxCells', area[0]), { ownerUid: c.uid, capturedAtMillis: Date.now() });
  const laxQueued = setDoc(doc(b.db, 'laxCells', area[0]), {
    ownerUid: b.uid,
    capturedAtMillis: Date.now() - 60_000,
  }).catch(errorCode);
  const cSteal = await captureCells(c, 'g3-c', area);
  await new Promise((resolve) => setTimeout(resolve, 1000));
  check('연결이 끊긴 동안 B의 batch는 확정되지 않는다', queuedSettled === null);
  check('그동안 C가 9칸을 빼앗는다', cSteal.ok && cSteal.stolen === 9);

  proxy.setOnline(true);
  const reconnected = Date.now();
  await withTimeout(Promise.all([queuedCommit, laxQueued]), 180_000);
  results.offlineQueuedBatch = { result: queuedSettled, settledAfterReconnectMillis: Date.now() - reconnected };
  const owners = await cellOwners(a, area);
  check(
    '재연결 후 오래된 version의 대기 batch는 규칙에서 거부되고 C의 소유가 유지된다',
    queuedSettled === 'permission-denied' && owners.every((owner) => owner === c.uid),
    `대기 batch 결과 ${queuedSettled}, 재연결 후 ${results.offlineQueuedBatch.settledAfterReconnectMillis}ms`,
  );
  const lax = (await getDoc(doc(a.db, 'laxCells', area[0]))).data();
  check(
    '[위험 재현] 버전 조건이 없으면 오래된 오프라인 쓰기가 C의 최신 소유를 덮어쓴다',
    lax.ownerUid === b.uid,
    `laxCells 최종 소유자 ${lax.ownerUid === b.uid ? 'B' : 'C'}`,
  );
  check(
    '거부된 batch는 획득 기록도 남기지 않는다',
    !(await getDoc(doc(a.db, 'territoryCaptures', 'g3-b'))).exists(),
  );

  // 기기에 남긴 같은 획득 ID로 온라인 트랜잭션을 다시 실행한다.
  const resubmit = await captureCells(b, 'g3-b', area);
  check(
    '재연결 후 같은 획득 ID 트랜잭션 재제출은 현재 소유자를 다시 읽고 반영한다',
    resubmit.ok && !resubmit.duplicate && resubmit.stolen === 9,
    `빼앗은 칸 ${resubmit.stolen}`,
  );
  const again = await captureCells(b, 'g3-b', area);
  check('한 번 더 재제출하면 중복으로 처리된다', again.ok && again.duplicate);
  const invariant = await gridInvariant(a);
  check('오프라인 시험 후에도 칸 상태가 일관된다', invariant.problems.length === 0, invariant.problems.join(', '));
}

async function gridLimits([a, b]) {
  console.log('\n[G4] 격자: 한 번에 쓰는 칸 수');
  await clearFirestore();
  const big = block(0, 0, 20, 25).slice(0, 499);
  const first = await captureCells(a, 'g4-a', big);
  const steal = await captureCells(b, 'g4-b', big);
  results.gridLargeCapture = {
    cells: big.length,
    createMillis: first.millis,
    stealMillis: steal.millis,
    stealAttempts: steal.attempts,
  };
  check(
    '획득 기록 1개와 칸 499개를 한 트랜잭션으로 획득·탈취한다(칸마다 같은 획득 기록 getAfter)',
    first.ok && steal.ok && steal.stolen === 499,
    `생성 ${first.millis}ms, 탈취 ${steal.millis}ms`,
  );
  check(
    '칸 500개 획득은 후보 규칙(칸 499개 이하)에서 거부된다',
    !(await captureCells(a, 'g4-over', block(0, 0, 20, 25))).ok,
  );

  // 규칙과 무관하게 에뮬레이터가 쓰기 수 제한을 적용하는지 본다.
  const batch = writeBatch(a.db);
  for (let index = 0; index < 501; index++) {
    batch.set(doc(a.db, 'laxCells', `limit-${index}`), { ownerUid: a.uid });
  }
  try {
    await batch.commit();
    results.emulator501Writes = 'committed';
  } catch (error) {
    results.emulator501Writes = errorCode(error);
  }
  console.log(`   규칙 없는 컬렉션에 501개 batch: ${results.emulator501Writes}`);
}

async function gridViewport([a, b, c, d]) {
  console.log('\n[G5] 격자: 지도 범위 조회와 사용자별 면적');
  await clearFirestore();
  // 40x40 칸(geohash7)을 네 사용자가 400칸씩 나눠 소유한다.
  const owners = [a, b, c, d];
  for (let quadrant = 0; quadrant < 4; quadrant++) {
    const cells = block(Math.floor(quadrant / 2) * 20 - 20, (quadrant % 2) * 20 - 20, 20, 20);
    for (let start = 0; start < cells.length; start += 200) {
      const outcome = await captureCells(owners[quadrant], `g5-${quadrant}-${start}`, cells.slice(start, start + 200));
      if (!outcome.ok) throw new Error(`시드 실패 ${outcome.error}`);
    }
  }
  const size = cellSizeMeters(7);
  const viewport = boxAround(0, 0, 500, 300); // 약 1km x 600m 화면
  const exact = coverBox(viewport, 7);
  const prefixes = coverBox(viewport, 6);
  let fetched = 0;
  const queries = [];
  for (let start = 0; start < prefixes.length; start += 30) {
    queries.push(
      getDocs(query(collection(a.db, 'territoryCells'), where('gh6', 'in', prefixes.slice(start, start + 30)))),
    );
  }
  for (const snapshot of await Promise.all(queries)) fetched += snapshot.size;
  const visibleOwned = (await getDocs(collection(a.db, 'territoryCells'))).docs.filter((d) =>
    exact.includes(d.id),
  ).length;
  const counts = await Promise.all(
    owners.map(async (user) =>
      (await getCountFromServer(query(collection(a.db, 'territoryCells'), where('ownerUid', '==', user.uid)))).data()
        .count,
    ),
  );
  results.gridViewport = {
    cellMeters: { width: Math.round(size.width), height: Math.round(size.height) },
    seededCells: 1600,
    viewportMeters: '1000x600',
    gh6Prefixes: prefixes.length,
    inQueries: queries.length,
    documentsRead: fetched,
    ownedCellsInsideViewport: visibleOwned,
    perUserCellCount: counts,
    perUserAreaM2: counts.map((count) => Math.round(count * size.width * size.height)),
  };
  console.log('  ', JSON.stringify(results.gridViewport));
  check(
    'gh6 접두어 in 조회가 화면 안 소유 칸을 모두 돌려준다',
    fetched >= visibleOwned && visibleOwned > 0,
    `읽은 문서 ${fetched}, 화면 안 소유 칸 ${visibleOwned}`,
  );
  check('count() 집계로 사용자별 칸 수를 구한다', counts.every((count) => count === 400), counts.join(','));
}

// ---------------------------------------------------------------- 정확한 도형 모델

function shapeData(user, captureId, geometry, cover, version) {
  return { ownerUid: user.uid, captureId, geometryJson: JSON.stringify(geometry), cover, version };
}

// 색인 없이 조회 결과로 겹치는 도형을 찾은 뒤 트랜잭션에서 차집합을 쓴다.
async function capturePolygonByQuery(user, captureId, polygon, candidates) {
  return runTransaction(user.db, async (tx) => {
    const snapshots = await Promise.all(candidates.map((id) => tx.get(doc(user.db, 'territoryShapes', id))));
    for (const snapshot of snapshots) {
      if (!snapshot.exists()) continue;
      const data = snapshot.data();
      const rest = polygonClipping.difference(JSON.parse(data.geometryJson), polygon);
      if (rest.length === 0) tx.delete(snapshot.ref);
      else tx.set(snapshot.ref, { ...data, geometryJson: JSON.stringify(rest), version: data.version + 1 });
    }
    const cover = coverBox(multiPolygonBox([polygon]), 6);
    tx.set(doc(user.db, 'territoryShapes', captureId), shapeData(user, captureId, [polygon], cover, 1));
  });
}

// geohash6 지역 문서를 색인이자 잠금으로 쓴다. 지역 문서에서 후보 도형 ID를 읽고 지역 version을 올린다.
async function capturePolygonWithRegions(user, captureId, polygon, { maxAttempts = 5 } = {}) {
  let attempts = 0;
  const regions = coverBox(multiPolygonBox([polygon]), 6);
  try {
    const outcome = await runTransaction(
      user.db,
      async (tx) => {
        attempts++;
        const captureRef = doc(user.db, 'territoryShapeCaptures', captureId);
        if ((await tx.get(captureRef)).exists()) return { duplicate: true };
        const regionSnapshots = await Promise.all(
          regions.map((region) => tx.get(doc(user.db, 'territoryRegions', region))),
        );
        const candidateIds = [...new Set(regionSnapshots.flatMap((s) => (s.exists() ? s.data().shapeIds : [])))];
        const shapes = await Promise.all(candidateIds.map((id) => tx.get(doc(user.db, 'territoryShapes', id))));
        const removed = new Set();
        for (const shape of shapes) {
          if (!shape.exists()) continue;
          const data = shape.data();
          const rest = polygonClipping.difference(JSON.parse(data.geometryJson), polygon);
          if (rest.length === 0) {
            tx.delete(shape.ref);
            removed.add(shape.id);
          } else if (multiPolygonArea(rest) < multiPolygonArea(JSON.parse(data.geometryJson)) - 1e-6) {
            tx.set(shape.ref, { ...data, geometryJson: JSON.stringify(rest), version: data.version + 1 });
          }
        }
        tx.set(captureRef, { ownerUid: user.uid, createdAt: serverTimestamp() });
        tx.set(doc(user.db, 'territoryShapes', captureId), shapeData(user, captureId, [polygon], regions, 1));
        // 다른 지역에 남은 삭제 도형 ID는 읽는 쪽에서 없는 문서로 건너뛴다.
        regionSnapshots.forEach((snapshot) => {
          const ids = (snapshot.exists() ? snapshot.data().shapeIds : []).filter((id) => !removed.has(id));
          tx.set(snapshot.ref, {
            shapeIds: [...ids, captureId],
            version: (snapshot.exists() ? snapshot.data().version : 0) + 1,
          });
        });
        return { duplicate: false, candidates: candidateIds.length, removed: removed.size };
      },
      { maxAttempts },
    );
    return { ok: true, attempts, ...outcome };
  } catch (error) {
    return { ok: false, attempts, error: errorCode(error) };
  }
}

async function shapeOverlap(reader) {
  const shapes = (await getDocs(collection(reader.db, 'territoryShapes'))).docs.map((d) => ({
    id: d.id,
    ownerUid: d.data().ownerUid,
    geometry: JSON.parse(d.data().geometryJson),
    bytes: d.data().geometryJson.length,
  }));
  let overlap = 0;
  for (let i = 0; i < shapes.length; i++) {
    for (let j = i + 1; j < shapes.length; j++) {
      overlap += multiPolygonArea(polygonClipping.intersection(shapes[i].geometry, shapes[j].geometry));
    }
  }
  return { shapes, overlap };
}

async function polygonPhantom([, b, c]) {
  console.log('\n[P1] 정확한 도형: 조회 후 트랜잭션(색인 없음)');
  await clearFirestore();
  const polygonB = rectPolygon(0, 0, 300, 300);
  const polygonC = rectPolygon(150, 150, 300, 300);
  const regions = coverBox(multiPolygonBox([polygonB, polygonC]), 6);
  // 두 차량이 거의 동시에 주변 도형을 조회한다. 아직 아무 도형도 없다.
  const [seenByB, seenByC] = await Promise.all(
    [b, c].map((user) =>
      getDocs(query(collection(user.db, 'territoryShapes'), where('cover', 'array-contains-any', regions))),
    ),
  );
  await Promise.all([
    capturePolygonByQuery(b, 'p1-b', polygonB, seenByB.docs.map((d) => d.id)),
    capturePolygonByQuery(c, 'p1-c', polygonC, seenByC.docs.map((d) => d.id)),
  ]);
  const { overlap } = await shapeOverlap(b);
  results.polygonWithoutIndexOverlapM2 = Math.round(overlap);
  check(
    '[위험 재현] 쿼리는 트랜잭션에 포함되지 않아 동시에 만든 두 도형이 겹친 채 저장된다',
    overlap > 1000,
    `겹친 면적 약 ${Math.round(overlap)}m²`,
  );
}

async function polygonWithRegions([a, b, c, d, e, f]) {
  console.log('\n[P2] 정확한 도형: 지역 색인 트랜잭션');
  await clearFirestore();
  const base = await capturePolygonWithRegions(a, 'p2-a', rectPolygon(0, 0, 400, 400));
  check('A가 400m x 400m 영역을 획득한다', base.ok);
  const polygons = [
    rectPolygon(-100, -100, 250, 250),
    rectPolygon(250, 250, 300, 300),
    rectPolygon(100, -50, 150, 500),
    rectPolygon(-50, 150, 500, 120),
    rectPolygon(180, 180, 60, 60),
  ];
  const outcomes = await Promise.all(
    [b, c, d, e, f].map((user, index) =>
      retryOnDenied(() => capturePolygonWithRegions(user, `p2-${user.name}`, polygons[index])),
    ),
  );
  const { shapes, overlap } = await shapeOverlap(a);
  const succeededPolygons = polygons.filter((_, index) => outcomes[index].ok);
  const expectedUnion = multiPolygonArea(polygonClipping.union(rectPolygon(0, 0, 400, 400), ...succeededPolygons));
  const storedArea = shapes.reduce((sum, shape) => sum + multiPolygonArea(shape.geometry), 0);
  results.polygonWithRegions = {
    ...summarize(outcomes),
    storedShapes: shapes.length,
    overlapM2: Number(overlap.toFixed(3)),
    storedAreaM2: Math.round(storedArea),
    expectedUnionM2: Math.round(expectedUnion),
    maxVertices: Math.max(...shapes.map((shape) => vertexCount(shape.geometry))),
    maxGeometryJsonChars: Math.max(...shapes.map((shape) => shape.bytes)),
  };
  console.log('  ', JSON.stringify(results.polygonWithRegions));
  check(
    '지역 색인을 함께 읽고 쓰면 동시 획득(앱 재시도 포함)이 모두 반영되고 도형이 겹치지 않는다',
    succeededPolygons.length === polygons.length && overlap < 1 && Math.abs(storedArea - expectedUnion) < 1,
    `겹침 ${overlap.toFixed(3)}m², 저장 면적 ${Math.round(storedArea)} / 합집합 ${Math.round(expectedUnion)}m²`,
  );
  const duplicate = await capturePolygonWithRegions(b, 'p2-B', polygons[0]);
  check('도형 모델도 같은 획득 ID 재시도는 중복으로 처리한다', duplicate.ok && duplicate.duplicate);
}

async function polygonViewport([a]) {
  console.log('\n[P3] 정확한 도형: 지도 범위 조회');
  await clearFirestore();
  // 200m 간격으로 150m 정사각형 100개를 만든다.
  for (let row = 0; row < 10; row++) {
    for (let col = 0; col < 10; col++) {
      const outcome = await capturePolygonWithRegions(
        a,
        `p3-${row}-${col}`,
        rectPolygon(col * 200 - 1000, row * 200 - 1000, 150, 150),
      );
      if (!outcome.ok) throw new Error(`시드 실패 ${outcome.error}`);
    }
  }
  const viewport = boxAround(0, 0, 500, 300);
  const prefixes = coverBox(viewport, 6);
  const snapshot = await getDocs(
    query(collection(a.db, 'territoryShapes'), where('cover', 'array-contains-any', prefixes.slice(0, 30))),
  );
  const visible = snapshot.docs.filter((d) => {
    const box = multiPolygonBox(JSON.parse(d.data().geometryJson));
    return box.maxLat >= viewport.minLat && box.minLat <= viewport.maxLat && box.maxLon >= viewport.minLon &&
      box.minLon <= viewport.maxLon;
  }).length;
  results.polygonViewport = {
    seededShapes: 100,
    gh6Prefixes: prefixes.length,
    documentsRead: snapshot.size,
    shapesInsideViewport: visible,
  };
  console.log('  ', JSON.stringify(results.polygonViewport));
  check(
    'cover 배열의 array-contains-any 조회가 화면과 겹치는 도형을 돌려준다',
    snapshot.size >= visible && visible > 0,
    `읽은 문서 ${snapshot.size}, 화면과 겹친 도형 ${visible}`,
  );
}

// ---------------------------------------------------------------- 실행

const users = [];
for (const name of ['A', 'B', 'C', 'D', 'E', 'F']) users.push(await createUser(name));
const anonymous = await createUser('anonymous', false);
const proxy = await createProxy(FIRESTORE_HOST);
const offline = { user: await createUser('B-offline', true, proxy.host), proxy };

try {
  await gridConcurrentSteal(users);
  await gridDuplicateSubmission(users, anonymous);
  await gridOfflineRetry(users, offline);
  await gridLimits(users);
  await gridViewport(users);
  await polygonPhantom(users);
  await polygonWithRegions(users);
  await polygonViewport(users);
} finally {
  await Promise.all([...users, anonymous, offline.user].map((user) => deleteApp(user.app)));
  proxy.setOnline(false);
  await proxy.close();
}

console.log('\nRESULTS', JSON.stringify(results, null, 2));
const failed = checks.filter((item) => !item.ok);
console.log(`\n${checks.length - failed.length}/${checks.length} checks passed`);
process.exit(failed.length === 0 ? 0 : 1);
