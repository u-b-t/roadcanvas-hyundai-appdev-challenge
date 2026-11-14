package kr.roadcanvas.app.territory

import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.TerritoryResult

/** 획득을 만든 뒤 서버 반영을 시도하는 기한(24시간). 지나면 반영을 포기한다. */
const val TERRITORY_SUBMIT_DEADLINE_MILLIS = 24 * 60 * 60 * 1000L

/** 경합으로 거부된 획득을 한 번 제출할 때 실행하는 최대 횟수 */
const val TERRITORY_SUBMIT_MAX_ATTEMPTS = 5

/** 지도 조회 한 번에 읽는 최대 칸 수. 넘으면 확대하라고 안내한다. */
const val TERRITORY_MAP_MAX_CELLS = 300

/** 지도 조회 한 번에 쓰는 최대 구역 수. 넘으면 확대하라고 안내한다. */
const val TERRITORY_MAP_MAX_REGIONS = 60

/** 순위표에 보이는 사용자 수 */
const val TERRITORY_RANKING_SIZE = 10

/** 경합 재실행 전 기다리는 기본 시간. 시도할 때마다 이 값만큼 늘린다. */
private const val CONFLICT_RETRY_DELAY_MILLIS = 300L

/** 서버 땅따먹기 요청 결과. 실패하면 예외 대신 [Failure]로 전달한다. */
sealed interface TerritoryCall<out T> {
    data class Success<out T>(val value: T) : TerritoryCall<T>

    data class Failure(val reason: TerritoryFailure) : TerritoryCall<Nothing>
}

/** 완료 기록에서 만든 획득의 상태. 화면은 `when`으로 나눠 보여준다. */
sealed interface TerritoryCaptureState {
    /** 칸이 [maxCells]개를 넘어 획득하지 않았다. 기기 기록은 남는다. */
    data class TooLarge(val maxCells: Int) : TerritoryCaptureState

    /** 지금 서버에 제출하는 중이다. */
    data class Submitting(val cellCount: Int) : TerritoryCaptureState

    /**
     * 기기에 보관했지만 아직 서버에 반영하지 못했다. 앱 실행, 결과 화면, 영토 화면 진입 때 다시 제출한다.
     * [failure]는 마지막 제출이 실패한 이유이며 아직 제출하지 않았으면 null이다.
     */
    data class Pending(val cellCount: Int, val failure: TerritoryFailure?) : TerritoryCaptureState

    /** 서버에 반영했다. */
    data class Submitted(val outcome: TerritoryCaptureOutcome) : TerritoryCaptureState

    /** 기한 안에 반영하지 못해 포기했다. 화면은 "서버에 반영하지 못함"으로 표시한다. */
    data class Expired(val cellCount: Int) : TerritoryCaptureState
}

/** 지도 범위의 칸 조회 결과 */
sealed interface TerritoryMapCells {
    data class Loaded(val cells: List<TerritoryCell>) : TerritoryMapCells

    /** 범위가 넓거나 칸이 [maxCells]개를 넘어 읽지 않았다. 화면은 확대하라고 안내한다. */
    data class TooMany(val maxCells: Int) : TerritoryMapCells
}

/**
 * 서버 땅따먹기. 획득 제출과 재제출, 지도 칸, 내 칸 수, 순위를 [store] 위에서 처리한다.
 *
 * 획득 순서: 다각형을 칸으로 바꿈 → 획득 ID(UUID)와 칸을 기기에 저장 → 서버 트랜잭션.
 * 경합으로 거부되면 잠시 뒤 같은 획득 ID로 [TERRITORY_SUBMIT_MAX_ATTEMPTS]번까지 실행한다.
 * 연결 실패 등으로 반영하지 못하면 기기 기록을 남기고 [resubmitPending]에서 다시 제출한다.
 * 화면을 떠나도 진행 중인 제출과 상태 저장은 끝까지 한다.
 */
class TerritoryGame(
    private val store: TerritoryStore,
    private val captures: TerritoryCaptureRepository,
    private val ownerProvider: TerritoryOwnerProvider,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newCaptureId: () -> String = { UUID.randomUUID().toString() },
    private val conflictRetryDelayMillis: Long = CONFLICT_RETRY_DELAY_MILLIS,
    private val onFailure: (Throwable) -> Unit = {}
) {
    private val mutableInFlight = MutableStateFlow(emptySet<String>())

    /** 지금 제출하는 중인 획득 ID */
    val inFlight: StateFlow<Set<String>> = mutableInFlight.asStateFlow()

    /**
     * 이 실행에서 반영한 획득 ID별 결과. 이전 소유자별 빼앗은 칸은 기기에 저장하지 않으므로
     * 기기에서 다시 읽은 상태에 이 값을 쓴다. 프로세스가 다시 만들어지면 칸 수만 남는다.
     */
    private val detailedOutcomes = MutableStateFlow(emptyMap<String, TerritoryCaptureOutcome>())

    /**
     * 완료 기록 [drawingId]의 차지한 영역 [claimed]를 획득으로 제출한다. 같은 기록은 처음 만든 획득을 다시 쓴다.
     * #148 이전에 geohash 칸으로 만든 획득은 지금 격자 칸으로 새 획득을 만들어 바꾼다.
     * 서버에 반영하지 못해도 기기에 저장했으면 [TerritoryCaptureState.Pending]을 돌려준다.
     * 기기에 저장하지 못하면 [TerritoryFailure.STORAGE]이며 제출하지 않는다.
     */
    suspend fun capture(
        drawingId: Long,
        claimed: TerritoryResult.Claimed
    ): TerritoryCall<TerritoryCaptureState> = withContext(NonCancellable) {
        val existing = when (val found = captures.findByDrawing(drawingId)) {
            is StorageResult.Failure -> return@withContext storageFailure(found.cause)
            is StorageResult.Success -> found.value
        }
        if (existing != null && !existing.legacy) {
            return@withContext TerritoryCall.Success(submitStored(existing))
        }
        val cells = when (val result = territoryCellsOf(claimed.boundary)) {
            is TerritoryCellsResult.TooLarge ->
                return@withContext TerritoryCall.Success(
                    TerritoryCaptureState.TooLarge(result.maxCells)
                )
            is TerritoryCellsResult.Cells -> result.cells
        }
        val capture = StoredTerritoryCapture(
            captureId = newCaptureId(),
            drawingId = drawingId,
            cells = cells,
            createdAtMillis = clock(),
            status = CaptureSyncStatus.PENDING
        )
        captures.save(capture).let {
            if (it is StorageResult.Failure) return@withContext storageFailure(it.cause)
        }
        TerritoryCall.Success(submitStored(capture))
    }

    /**
     * 완료 기록 [drawingId]의 획득 상태. 획득을 만든 적이 없으면(너무 큰 영역 포함) null이다.
     * #148 이전 geohash 칸 획득은 지금 격자에서 읽을 수 없어 만든 적이 없는 것처럼 null이다. 다시 [capture]하면 새로 만든다.
     */
    suspend fun state(drawingId: Long): TerritoryCall<TerritoryCaptureState?> =
        when (val found = captures.findByDrawing(drawingId)) {
            is StorageResult.Failure -> storageFailure(found.cause)
            is StorageResult.Success ->
                TerritoryCall.Success(found.value?.takeUnless { it.legacy }?.let(::stateOf))
        }

    /**
     * 반영하지 못한 획득을 만든 순서대로 다시 제출한다. 기한이 지난 획득은 포기한다.
     * 결과는 이번에 반영한 획득 수다.
     */
    suspend fun resubmitPending(): TerritoryCall<Int> = withContext(NonCancellable) {
        val pending = when (val found = captures.pending()) {
            is StorageResult.Failure -> return@withContext storageFailure(found.cause)
            is StorageResult.Success -> found.value
        }
        TerritoryCall.Success(
            pending.count { submitStored(it) is TerritoryCaptureState.Submitted }
        )
    }

    /**
     * 지도 범위 [bounds]의 칸. 범위를 덮는 구역으로 조회한다.
     * 구역이 [TERRITORY_MAP_MAX_REGIONS]개를 넘거나 칸이 [TERRITORY_MAP_MAX_CELLS]개를 넘으면
     * [TerritoryMapCells.TooMany]다.
     */
    suspend fun mapCells(bounds: GeoBounds): TerritoryCall<TerritoryMapCells> = attempt {
        val tooMany = TerritoryMapCells.TooMany(TERRITORY_MAP_MAX_CELLS)
        val regions = TerritoryGrid.regionsCovering(bounds, TERRITORY_MAP_MAX_REGIONS)
            ?: return@attempt tooMany
        val uid = owner().uid
        val cells = store.cells(regions, TERRITORY_MAP_MAX_CELLS + 1)
        if (cells.size > TERRITORY_MAP_MAX_CELLS) return@attempt tooMany
        TerritoryMapCells.Loaded(
            // 칸 ID 형식이 아닌 문서는 그릴 수 없어 뺀다. 규칙이 막지만 지도 전체가 실패하지 않게 한다.
            cells.filter { TerritoryGrid.parse(it.id) != null }.map {
                TerritoryCell(it.id, it.ownerUid, it.ownerUid == uid)
            }
        )
    }

    /** 내 보유 칸 수. 면적은 [territoryAreaOf]로 환산한다. */
    suspend fun myCellCount(): TerritoryCall<Long> = attempt { store.ownedCellCount(owner().uid) }

    /** 보유 칸 수 내림차순 상위 [TERRITORY_RANKING_SIZE]명 */
    suspend fun ranking(): TerritoryCall<List<TerritoryRank>> = attempt {
        val uid = owner().uid
        store.topOwners(TERRITORY_RANKING_SIZE).mapIndexed { index, owner ->
            TerritoryRank(index + 1, owner.uid, owner.nickname, owner.cellCount, owner.uid == uid)
        }
    }

    private fun stateOf(capture: StoredTerritoryCapture): TerritoryCaptureState = when {
        capture.status == CaptureSyncStatus.SUBMITTED && capture.outcome != null ->
            TerritoryCaptureState.Submitted(
                detailedOutcomes.value[capture.captureId] ?: capture.outcome
            )
        capture.status == CaptureSyncStatus.EXPIRED || expired(capture) ->
            TerritoryCaptureState.Expired(capture.cells.size)
        capture.captureId in mutableInFlight.value ->
            TerritoryCaptureState.Submitting(capture.cells.size)
        else -> TerritoryCaptureState.Pending(capture.cells.size, failure = null)
    }

    /** 기기에 보관한 획득을 제출한다. 같은 획득을 제출 중이면 기다리지 않고 제출 중으로 돌려준다. */
    private suspend fun submitStored(capture: StoredTerritoryCapture): TerritoryCaptureState {
        if (capture.status != CaptureSyncStatus.PENDING) return stateOf(capture)
        if (expired(capture)) {
            save(capture.copy(status = CaptureSyncStatus.EXPIRED))
            return TerritoryCaptureState.Expired(capture.cells.size)
        }
        var started = false
        mutableInFlight.update {
            if (capture.captureId in it) {
                it
            } else {
                started = true
                it + capture.captureId
            }
        }
        if (!started) return TerritoryCaptureState.Submitting(capture.cells.size)
        return try {
            when (val result = attemptSubmit(capture)) {
                is TerritoryCall.Failure ->
                    TerritoryCaptureState.Pending(capture.cells.size, result.reason)
                is TerritoryCall.Success -> {
                    detailedOutcomes.update { it + (capture.captureId to result.value) }
                    save(capture.copy(status = CaptureSyncStatus.SUBMITTED, outcome = result.value))
                    TerritoryCaptureState.Submitted(result.value)
                }
            }
        } finally {
            mutableInFlight.update { it - capture.captureId }
        }
    }

    private suspend fun attemptSubmit(
        capture: StoredTerritoryCapture
    ): TerritoryCall<TerritoryCaptureOutcome> = attempt {
        submitWithRetry(owner(), TerritoryCaptureRequest(capture.captureId, capture.cells))
    }

    /** 경합 거부는 잠시 뒤 같은 요청으로 다시 실행하고, 마지막 시도의 오류는 그대로 던진다. */
    private suspend fun submitWithRetry(
        owner: TerritoryOwner,
        request: TerritoryCaptureRequest
    ): TerritoryCaptureOutcome {
        repeat(TERRITORY_SUBMIT_MAX_ATTEMPTS - 1) { index ->
            try {
                return store.submit(owner, request)
            } catch (e: TerritoryStoreException) {
                if (e.failure != TerritoryFailure.CONFLICT) throw e
                onFailure(e)
                delay(conflictRetryDelayMillis * (index + 1))
            }
        }
        return store.submit(owner, request)
    }

    /**
     * 반영을 포기할 획득. 기한이 지났거나 #148 이전에 만든 geohash 칸 획득이다.
     * geohash 칸은 지금 격자 컬렉션에 제출할 수 없어 다시 제출하지 않는다.
     */
    private fun expired(capture: StoredTerritoryCapture) =
        clock() - capture.createdAtMillis >= TERRITORY_SUBMIT_DEADLINE_MILLIS || capture.legacy

    /** #148 이전에 geohash 칸으로 만든 획득 */
    private val StoredTerritoryCapture.legacy: Boolean
        get() = cells.any { TerritoryGrid.parse(it) == null }

    /** 서버 반영 뒤 기기 상태를 저장하지 못해도 다음 재제출이 중복으로 처리되므로 결과는 그대로 둔다. */
    private suspend fun save(capture: StoredTerritoryCapture) {
        val saved = captures.save(capture)
        if (saved is StorageResult.Failure) onFailure(saved.cause)
    }

    private suspend fun owner(): TerritoryOwner = try {
        ownerProvider.current()
    } catch (e: CancellationException) {
        throw e
    } catch (e: TerritoryStoreException) {
        throw e
    } catch (e: Exception) {
        throw TerritoryStoreException(TerritoryFailure.AUTH, "익명 계정을 확인하지 못했습니다.", e)
    }

    private suspend fun <T> attempt(block: suspend () -> T): TerritoryCall<T> = try {
        TerritoryCall.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        TerritoryCall.Failure(failureOf(e))
    }

    private fun failureOf(error: Throwable): TerritoryFailure = when (error) {
        is TerritoryStoreException -> error.failure
        is IOException -> TerritoryFailure.NETWORK
        else -> TerritoryFailure.UNKNOWN
    }

    private fun storageFailure(cause: Throwable): TerritoryCall<Nothing> {
        onFailure(cause)
        return TerritoryCall.Failure(TerritoryFailure.STORAGE)
    }
}
