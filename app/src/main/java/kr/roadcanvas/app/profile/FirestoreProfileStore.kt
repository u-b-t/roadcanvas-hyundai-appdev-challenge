package kr.roadcanvas.app.profile

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Firestore 요청 하나를 기다리는 최대 시간. 응답이 없으면 연결 실패로 본다. */
private const val REQUEST_TIMEOUT_MILLIS = 20_000L

/**
 * Firestore 프로필 저장소. 오래된 캐시를 보이지 않도록 서버에서만 읽는다.
 * 닉네임은 `users/{uid}`, `leaderboards/circle/entries/{uid}`, `territoryOwners/{uid}`와
 * 내 `artworks`(`ownerUid`가 나인 문서)에 있으며, 있는 문서만 한 배치로 바꾼다.
 * 서버에서 먼저 읽으므로 오프라인이면 쓰기를 대기열에 남기지 않고 연결 실패로 끝난다.
 */
class FirestoreProfileStore(
    private val db: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : ProfileStore {
    private fun circleEntry(uid: String) =
        db().collection("leaderboards").document("circle").collection("entries").document(uid)

    private fun territoryOwner(uid: String) = db().collection("territoryOwners").document(uid)

    private fun myArtworks(uid: String) = db().collection("artworks").whereEqualTo("ownerUid", uid)

    override suspend fun summary(uid: String): ProfileSummary = request {
        val entry = circleEntry(uid).get(Source.SERVER).awaitResult()
        val owner = territoryOwner(uid).get(Source.SERVER).awaitResult()
        val artworks = myArtworks(uid).get(Source.SERVER).awaitResult()
        ProfileSummary(
            circleBestScore = entry.getLong("score")?.toInt(),
            territoryCellCount = owner.getLong("cellCount") ?: 0L,
            publishedArtworkCount = artworks.documents.count {
                it.getString("visibility") == "PUBLISHED"
            }
        )
    }

    override suspend fun updateNickname(uid: String, nickname: String) = request {
        val entry = circleEntry(uid).get(Source.SERVER).awaitResult()
        val owner = territoryOwner(uid).get(Source.SERVER).awaitResult()
        val artworks = myArtworks(uid).get(Source.SERVER).awaitResult()
        val batch = db().batch()
        batch.update(db().collection("users").document(uid), "nickname", nickname)
        if (entry.exists()) batch.update(entry.reference, "nickname", nickname)
        if (owner.exists()) batch.update(owner.reference, "nickname", nickname)
        artworks.documents.forEach { batch.update(it.reference, "nickname", nickname) }
        batch.commit().awaitResult()
        Unit
    }

    /** Firestore 오류를 실패 이유로 바꾼다. */
    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (e: FirebaseFirestoreException) {
        val failure = when (e.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED -> ProfileFailure.NETWORK
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> ProfileFailure.AUTH
            else -> ProfileFailure.UNKNOWN
        }
        throw ProfileStoreException(failure, "Firestore 요청 실패: ${e.code}", e)
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { await() }
            ?: throw ProfileStoreException(ProfileFailure.NETWORK, "Firestore 응답이 없습니다.")
}
