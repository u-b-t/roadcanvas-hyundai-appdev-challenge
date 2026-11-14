// seed.mjs로 넣은 데모 데이터를 클라이언트 권한으로 지울 수 있는 만큼 지운다. docs/firebase-demo-seed.md 참고.
// 규칙상 작품 문서와 영토 칸·획득 기록·순위 문서는 지울 수 없어 작품은 비공개(DRAFT)로, 순위 칸 수는 0으로 바꾼다.
// --delete-accounts를 주면 마지막에 익명 계정도 지운다. 그 뒤로는 남은 문서를 클라이언트로 고칠 수 없다.
import { collection, deleteDoc, doc, getDoc, getDocs, setLogLevel, updateDoc } from 'firebase/firestore';
import { demoUser, firebaseConfig, STATE_PATH } from './client.mjs';
import { ARTWORKS, CIRCLES, USERS } from './data.mjs';

setLogLevel('silent');
const deleteAccounts = process.argv.includes('--delete-accounts');
const config = firebaseConfig();
console.log(`프로젝트 ${config.projectId}, 상태 파일 ${STATE_PATH}`);
const users = {};
for (const spec of USERS) {
  const user = await demoUser(config, spec.key, spec.nickname, { create: false });
  if (user) users[spec.key] = user;
  else console.log(`사용자 ${spec.key}: 상태 파일에 기록이 없어 건너뜀`);
}

for (const artwork of ARTWORKS) {
  for (const voter of artwork.voters) {
    if (users[voter]) await deleteDoc(doc(users[voter].db, 'artworks', artwork.id, 'votes', users[voter].uid));
  }
  const owner = users[artwork.owner];
  if (!owner) continue;
  const ref = doc(owner.db, 'artworks', artwork.id);
  try {
    if ((await getDoc(ref)).data()?.visibility === 'PUBLISHED') await updateDoc(ref, { visibility: 'DRAFT' });
    console.log(`작품 artworks/${artwork.id} 비공개, 투표 삭제`);
  } catch (error) {
    if (error.code !== 'permission-denied') throw error;
    console.log(`작품 artworks/${artwork.id} 없음`);
  }
}

for (const circle of CIRCLES) {
  const user = users[circle.owner];
  if (!user) continue;
  await deleteDoc(doc(user.db, 'leaderboards', 'circle', 'entries', user.uid));
  const ref = doc(user.db, 'users', user.uid, 'drawings', circle.drawingId);
  for (const chunk of (await getDocs(collection(ref, 'chunks'))).docs) await deleteDoc(chunk.ref);
  await deleteDoc(ref);
  console.log(`동그라미 users/${user.uid}/drawings/${circle.drawingId}와 순위 삭제`);
}

for (const user of Object.values(users)) {
  const owner = doc(user.db, 'territoryGridOwners', user.uid);
  if ((await getDoc(owner)).exists()) await updateDoc(owner, { cellCount: 0 });
  await deleteDoc(doc(user.db, 'users', user.uid));
  console.log(`사용자 ${user.key} ${user.uid}: 영토 순위 0칸, 프로필 삭제`);
  if (deleteAccounts) {
    await user.auth.currentUser.delete();
    console.log(`사용자 ${user.key} ${user.uid}: 익명 계정 삭제`);
  }
}
console.log('완료. 영토 칸과 획득 기록은 남는다(규칙상 삭제 불가).');
process.exit(0);
