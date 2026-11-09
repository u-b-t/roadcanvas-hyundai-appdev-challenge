// 데모 사용자를 앱과 같은 방식(Firebase JS SDK 익명 인증)으로 로그인시킨다.
// 익명 계정은 로그아웃하면 다시 찾을 수 없으므로 SDK가 저장하는 로그인 정보(갱신 토큰 포함)를
// 저장소 밖 상태 파일에 남기고, 다시 실행하면 같은 UID로 로그인한다.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp } from 'firebase/app';
import { connectAuthEmulator, initializeAuth, signInAnonymously } from 'firebase/auth';
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore';

const HERE = dirname(fileURLToPath(import.meta.url));
const EXPECTED_PROJECT_ID = 'roadcanvas-f9f2a';

export const STATE_PATH = resolve(
  process.env.DEMO_SEED_STATE ?? join(homedir(), '.roadcanvas-demo-seed', 'state.json'),
);

/** API 키와 프로젝트를 환경 변수 또는 google-services.json에서 읽는다. 키는 저장소에 넣지 않는다. */
export function firebaseConfig() {
  const servicesPath = resolve(
    process.env.GOOGLE_SERVICES_JSON ?? join(HERE, '..', '..', 'app', 'google-services.json'),
  );
  let services = null;
  if (existsSync(servicesPath)) services = JSON.parse(readFileSync(servicesPath, 'utf8'));
  const projectId = process.env.FIREBASE_PROJECT_ID ?? services?.project_info?.project_id;
  const apiKey = process.env.FIREBASE_API_KEY ?? services?.client?.[0]?.api_key?.[0]?.current_key;
  if (!projectId || !apiKey) {
    throw new Error(`API 키가 없습니다. FIREBASE_API_KEY와 FIREBASE_PROJECT_ID 또는 ${servicesPath}를 준비하세요.`);
  }
  if (projectId !== EXPECTED_PROJECT_ID && process.env.DEMO_SEED_ALLOW_OTHER_PROJECT !== '1') {
    throw new Error(`예상한 프로젝트(${EXPECTED_PROJECT_ID})가 아닙니다: ${projectId}`);
  }
  return { projectId, apiKey, authDomain: `${projectId}.firebaseapp.com` };
}

function loadState() {
  if (!existsSync(STATE_PATH)) return { auth: {}, users: {} };
  return JSON.parse(readFileSync(STATE_PATH, 'utf8'));
}

const state = loadState();

function saveState() {
  mkdirSync(dirname(STATE_PATH), { recursive: true });
  writeFileSync(STATE_PATH, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
}

/** Firebase Auth 영속성 구현. SDK가 이 클래스로 만든 인스턴스에 로그인 정보를 읽고 쓴다. */
class FileStatePersistence {
  static type = 'LOCAL';
  type = 'LOCAL';
  async _isAvailable() {
    return true;
  }
  async _set(key, value) {
    state.auth[key] = value;
    saveState();
  }
  async _get(key) {
    return state.auth[key] ?? null;
  }
  async _remove(key) {
    delete state.auth[key];
    saveState();
  }
  _addListener() {}
  _removeListener() {}
}

/**
 * 데모 사용자 [key]로 로그인한다. 상태 파일에 기록한 UID와 다른 사용자로 로그인되면(갱신 토큰 만료 등) 멈춘다.
 * [create]가 false이면 기록이 없는 사용자를 새로 만들지 않고 null을 돌려준다.
 */
export async function demoUser(config, key, nickname, { create = true } = {}) {
  const app = initializeApp(config, `demo-${key}`);
  const auth = initializeAuth(app, { persistence: FileStatePersistence });
  const db = getFirestore(app);
  // firebase emulators:exec가 설정하는 변수. 운영에 쓰기 전 에뮬레이터에서 시험할 때만 쓴다.
  if (process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  }
  if (process.env.FIRESTORE_EMULATOR_HOST) {
    const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
    connectFirestoreEmulator(db, host, Number(port));
  }
  await auth.authStateReady();
  const recorded = state.users[key];
  if (!auth.currentUser) {
    if (recorded) throw new Error(`${key}(${recorded})의 로그인 정보를 복원하지 못했습니다. ${STATE_PATH}를 확인하세요.`);
    if (!create) return null;
    await signInAnonymously(auth);
  }
  const uid = auth.currentUser.uid;
  if (recorded && recorded !== uid) throw new Error(`${key}: 기록한 UID ${recorded}와 로그인한 UID ${uid}가 다릅니다.`);
  if (!recorded) {
    state.users[key] = uid;
    saveState();
  }
  return { key, uid, nickname, auth, db };
}
