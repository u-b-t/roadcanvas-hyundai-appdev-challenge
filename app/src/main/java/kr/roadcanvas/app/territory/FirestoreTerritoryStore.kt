package kr.roadcanvas.app.territory

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.TransactionOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kr.roadcanvas.app.account.defaultNickname

// geohash 칸을 쓰던 territoryCaptures·territoryCells·territoryOwners 대신 정사각형 격자용 컬렉션을 쓴다(#148).
private const val CAPTURES = "territoryGridCaptures"
private const val CELLS = "territoryGridCells"
private const val OWNERS = "territoryGridOwners"

/** Firestore `in` 조건에 넣을 수 있는 최대 값 수 */
private const val IN_QUERY_MAX_VALUES = 30

/** 트랜잭션 하나에서 SDK가 충돌로 다시 실행하는 최대 횟수 */
private const val TRANSACTION_MAX_ATTEMPTS = 5

/** Firestore 요청 하나를 기다리는 최대 시간. 오프라인 트랜잭션은 SDK가 몇 번 시도한 뒤 실패한다. */
private const val REQUEST_TIMEOUT_MILLIS = 30_000L

/**
 * Firestore 땅따먹기 저장소. 서버 계약과 규칙은 docs/firebase-territory.md를 따른다.
 * 이전 소유자별 빼앗은 칸은 트랜잭션에서 읽은 칸과 순위 문서로 만들어 응답에만 담고 획득 기록에는 쓰지 않는다.
 *
 * 획득은 온라인 트랜잭션 하나로 반영한다. 획득 기록을 먼저 읽어 이미 있으면 그때 결과를 돌려주고,
 * 없으면 칸과 순위 문서를 모두 읽은 뒤 획득 기록 생성, 칸 소유자 덮어쓰기(version +1), 보유 칸 수 증감을 함께 쓴다.
 * 오프라인 대기 쓰기(batch)는 쓰지 않는다. 조회는 오래된 캐시를 보이지 않도록 서버에서 읽는다.
 */
class FirestoreTerritoryStore(
    private val db: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : TerritoryStore {
    override suspend fun submit(
        owner: TerritoryOwner,
        request: TerritoryCaptureRequest
    ): TerritoryCaptureOutcome {
        require(request.cells.size in 1..TERRITORY_MAX_CELLS_PER_CAPTURE) {
            "칸 수가 범위를 벗어났습니다: ${request.cells.size}"
        }
        val firestore = db()
        val captureRef = firestore.collection(CAPTURES).document(request.captureId)
        val cellRefs = request.cells.map { firestore.collection(CELLS).document(it) }
        val ownerRef = firestore.collection(OWNERS).document(owner.uid)
        val options = TransactionOptions.Builder().setMaxAttempts(TRANSACTION_MAX_ATTEMPTS).build()
        return request {
            firestore.runTransaction(options) { transaction ->
                val existing = transaction.get(captureRef)
                if (existing.exists()) return@runTransaction existing.toOutcome(owner.uid)

                val cells = cellRefs.map { transaction.get(it) }
                val previousOwners = cells.map { it.getString("ownerUid") }
                val stolenByOwner = previousOwners.filterNotNull()
                    .filter { it != owner.uid }
                    .groupingBy { it }
                    .eachCount()
                val victims = stolenByOwner.keys.associateWith {
                    transaction.get(firestore.collection(OWNERS).document(it))
                }
                val outcome = territoryCaptureOutcomeOf(
                    owner.uid,
                    request.cells.zip(previousOwners).toMap()
                ) { uid -> victims[uid]?.getString("nickname") ?: defaultNickname(uid) }
                val ownerCount = transaction.get(ownerRef).getLong("cellCount") ?: 0L

                transaction.set(
                    captureRef,
                    mapOf(
                        "ownerUid" to owner.uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "cells" to request.cells,
                        "cellCount" to request.cells.size,
                        "newCellCount" to outcome.newCells,
                        "stolenCellCount" to outcome.stolenCells,
                        "ownCellCount" to outcome.ownCells
                    )
                )
                cells.forEachIndexed { index, cell ->
                    transaction.set(
                        cellRefs[index],
                        mapOf(
                            "ownerUid" to owner.uid,
                            "captureId" to request.captureId,
                            "capturedAt" to FieldValue.serverTimestamp(),
                            "version" to (cell.getLong("version") ?: 0L) + 1,
                            "region" to TerritoryGrid.regionOf(cell.id)
                        )
                    )
                }
                transaction.set(
                    ownerRef,
                    mapOf(
                        "nickname" to owner.nickname,
                        "cellCount" to ownerCount + outcome.newCells + outcome.stolenCells
                    ),
                    SetOptions.merge()
                )
                victims.forEach { (uid, snapshot) ->
                    // 순위 문서가 없는 사용자는 줄일 칸 수가 없다.
                    if (!snapshot.exists()) return@forEach
                    val count = snapshot.getLong("cellCount") ?: 0L
                    val next = (count - stolenByOwner.getValue(uid)).coerceAtLeast(0L)
                    // 규칙은 줄어드는 갱신만 허용하므로 이미 0이면 쓰지 않는다.
                    if (next == count) return@forEach
                    transaction.update(
                        snapshot.reference,
                        mapOf("cellCount" to next, "lastLossCaptureId" to request.captureId)
                    )
                }
                outcome
            }
        }
    }

    override suspend fun cells(regions: List<String>, limit: Int): List<StoredTerritoryCell> {
        val found = mutableListOf<StoredTerritoryCell>()
        for (chunk in regions.distinct().chunked(IN_QUERY_MAX_VALUES)) {
            val remaining = limit - found.size
            if (remaining <= 0) break
            val documents = request {
                db().collection(CELLS)
                    .whereIn("region", chunk)
                    .limit(remaining.toLong())
                    .get(Source.SERVER)
            }.documents
            documents.mapNotNullTo(found) { document ->
                document.getString("ownerUid")?.let { StoredTerritoryCell(document.id, it) }
            }
        }
        return found
    }

    override suspend fun ownedCellCount(uid: String): Long = request {
        db().collection(OWNERS).document(uid).get(Source.SERVER)
    }.getLong("cellCount") ?: 0L

    override suspend fun topOwners(limit: Int): List<StoredTerritoryOwner> = request {
        db().collection(OWNERS)
            .orderBy("cellCount", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get(Source.SERVER)
    }.documents.mapNotNull { document ->
        val nickname = document.getString("nickname") ?: return@mapNotNull null
        val count = document.getLong("cellCount") ?: return@mapNotNull null
        StoredTerritoryOwner(document.id, nickname, count)
    }

    /** 이미 반영된 획득 기록의 결과. 다른 사용자의 획득 ID면 거부한다. */
    private fun DocumentSnapshot.toOutcome(uid: String): TerritoryCaptureOutcome {
        if (getString("ownerUid") != uid) {
            throw TerritoryStoreException(TerritoryFailure.UNKNOWN, "다른 사용자의 획득 ID입니다.")
        }
        return TerritoryCaptureOutcome(
            newCells = getLong("newCellCount")?.toInt() ?: 0,
            stolenCells = getLong("stolenCellCount")?.toInt() ?: 0,
            ownCells = getLong("ownCellCount")?.toInt() ?: 0
        )
    }

    /** 요청을 기다리고, 시간 초과와 Firestore 오류를 실패 이유로 바꾼다. */
    private suspend fun <T> request(block: () -> Task<T>): T {
        val result = try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) { Result.success(block().await()) }
        } catch (e: FirebaseFirestoreException) {
            throw TerritoryStoreException(territoryFailureOf(e.code), e.message ?: e.code.name, e)
        }
        result ?: throw TerritoryStoreException(TerritoryFailure.NETWORK, "Firestore 응답이 없습니다.")
        return result.getOrThrow()
    }
}

/**
 * Firestore 오류 코드를 실패 이유로 바꾼다. 트랜잭션이 읽은 칸을 다른 획득이 먼저 바꾸면 칸 version 규칙 때문에
 * 충돌 대신 `PERMISSION_DENIED`로 끝나는 경우가 있어(docs/firebase-territory-ownership.md) 경합으로 본다.
 */
fun territoryFailureOf(code: FirebaseFirestoreException.Code): TerritoryFailure = when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.ABORTED -> TerritoryFailure.CONFLICT
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> TerritoryFailure.NETWORK
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> TerritoryFailure.AUTH
    else -> TerritoryFailure.UNKNOWN
}
