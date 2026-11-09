package kr.roadcanvas.app.territory

/** 칸을 획득하는 사용자. [uid]는 인증 사용자 UID이고 [nickname]은 순위에 보일 닉네임이다. */
data class TerritoryOwner(val uid: String, val nickname: String)

/** 획득할 사용자를 준다. 인증하지 못하면 예외를 던진다. */
fun interface TerritoryOwnerProvider {
    suspend fun current(): TerritoryOwner
}

/**
 * 서버에 제출하는 획득 하나. [captureId]는 처음 만들 때 한 번 정하는 UUID이며 다시 제출해도 같은 값을 쓴다.
 * [cells]는 geohash7 칸 목록(1~[TERRITORY_MAX_CELLS_PER_CAPTURE]개)이다.
 */
data class TerritoryCaptureRequest(val captureId: String, val cells: List<String>)

/** 획득으로 이전 소유자 [uid]([nickname])에게서 빼앗은 칸 [cells] */
data class TerritoryLoss(val uid: String, val nickname: String, val cells: List<String>)

/**
 * 획득을 반영한 결과. 획득한 칸을 반영 직전 소유자로 나눈다.
 *
 * [losses]와 [ownCellIds]는 반영한 제출의 응답에서만 알 수 있다. 서버 획득 기록과 기기에는 칸 수만 남기므로
 * 이미 반영된 획득을 다시 제출하거나 기기에서 다시 읽은 결과는 비어 있다.
 *
 * @property newCells 소유자가 없던 칸 수
 * @property stolenCells 다른 사용자에게서 빼앗은 칸 수
 * @property ownCells 이미 내 칸이라 획득 시각만 갱신한 칸 수
 * @property losses 빼앗은 칸을 이전 소유자별로 나눈 것. 빼앗은 칸 수가 많은 소유자부터다.
 * @property ownCellIds 이미 내 칸이던 칸 ID
 */
data class TerritoryCaptureOutcome(
    val newCells: Int,
    val stolenCells: Int,
    val ownCells: Int,
    val losses: List<TerritoryLoss> = emptyList(),
    val ownCellIds: List<String> = emptyList()
) {
    val totalCells: Int get() = newCells + stolenCells + ownCells

    /** 칸 수만 남긴 결과. 서버 획득 기록과 기기에 보관하는 값이다. */
    fun countsOnly() = TerritoryCaptureOutcome(newCells, stolenCells, ownCells)
}

/**
 * 반영 직전 칸 소유자 [previousOwners](칸 ID → UID, 소유자가 없으면 null)로 [uid]의 획득 결과를 만든다.
 * 이전 소유자 닉네임은 [nicknameOf]로 찾는다.
 */
fun territoryCaptureOutcomeOf(
    uid: String,
    previousOwners: Map<String, String?>,
    nicknameOf: (String) -> String
): TerritoryCaptureOutcome {
    val stolen = previousOwners.filterValues { it != null && it != uid }
    val losses = stolen.entries.groupBy({ it.value!! }, { it.key })
        .map { (owner, cells) -> TerritoryLoss(owner, nicknameOf(owner), cells) }
        .sortedWith(compareByDescending<TerritoryLoss> { it.cells.size }.thenBy { it.uid })
    val own = previousOwners.filterValues { it == uid }.keys.toList()
    return TerritoryCaptureOutcome(
        newCells = previousOwners.count { it.value == null },
        stolenCells = stolen.size,
        ownCells = own.size,
        losses = losses,
        ownCellIds = own
    )
}

/** 지도에 그리는 칸 하나. [mine]이면 현재 사용자의 칸이다. */
data class TerritoryCell(val id: String, val ownerUid: String, val mine: Boolean) {
    /** 칸 범위. 지도에 다각형으로 그릴 때 [GeoBounds.corners]를 쓴다. */
    val bounds: GeoBounds get() = Geohash.bounds(id)
}

/** 순위표 한 줄. [rank]는 1부터 시작한다. [mine]이면 현재 사용자다. */
data class TerritoryRank(
    val rank: Int,
    val uid: String,
    val nickname: String,
    val cellCount: Long,
    val mine: Boolean
) {
    val areaSquareMeters: Double get() = territoryAreaOf(cellCount)
}

/** 서버 칸 문서 하나. 저장소 구현이 돌려주는 값이다. */
data class StoredTerritoryCell(val id: String, val ownerUid: String)

/** 순위 문서 하나. 저장소 구현이 보유 칸 수 내림차순으로 돌려준다. */
data class StoredTerritoryOwner(val uid: String, val nickname: String, val cellCount: Long)

/** 서버 땅따먹기 요청이 실패한 이유. 화면이 안내 문구를 고르는 데 쓴다. */
enum class TerritoryFailure {
    /** 서버에 연결하지 못했다. 미제출 획득은 나중에 다시 제출한다. */
    NETWORK,

    /** 익명 계정을 확인하지 못했다. */
    AUTH,

    /** 다른 사용자의 동시 획득과 겹쳐 서버 규칙이 거부했다. 앱이 잠시 뒤 같은 획득 ID로 다시 실행한다. */
    CONFLICT,

    /** 기기 기록을 읽거나 저장하지 못했다. */
    STORAGE,

    /** 서버가 요청을 거부했거나 알 수 없는 오류다. */
    UNKNOWN
}

/** 서버 저장소 오류를 실패 이유로 알린다. */
class TerritoryStoreException(
    val failure: TerritoryFailure,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 땅따먹기 서버 저장소. 서버 계약은 docs/firebase-territory.md를 따른다.
 * 실패하면 [TerritoryStoreException]이나 다른 예외를 던진다. 재시도는 [TerritoryGame]이 한다.
 */
interface TerritoryStore {
    /**
     * [owner]의 획득 [request]를 트랜잭션 하나로 반영한다. 획득 기록을 만들고, 칸 소유자를 덮어쓰고,
     * 내 보유 칸 수를 늘리고 빼앗긴 사용자의 보유 칸 수를 줄인다.
     * 같은 획득 ID가 이미 반영되어 있으면 아무것도 쓰지 않고 그때의 결과를 돌려준다.
     * 경합으로 서버 규칙이 거부하면 [TerritoryFailure.CONFLICT]다.
     */
    suspend fun submit(
        owner: TerritoryOwner,
        request: TerritoryCaptureRequest
    ): TerritoryCaptureOutcome

    /**
     * `gh6`가 [prefixes] 중 하나인 칸을 최대 [limit]개 읽는다. [limit]개를 채우면 더 있을 수 있다.
     */
    suspend fun cells(prefixes: List<String>, limit: Int): List<StoredTerritoryCell>

    /** [uid] 사용자의 보유 칸 수. 순위 문서가 없으면 0이다. */
    suspend fun ownedCellCount(uid: String): Long

    /** 보유 칸 수 내림차순 상위 [limit]명 */
    suspend fun topOwners(limit: Int): List<StoredTerritoryOwner>
}

/** 서버가 연결되지 않은 빌드에서 쓰는 저장소. 모든 요청이 연결 실패다. */
object UnavailableTerritoryStore : TerritoryStore {
    private fun unavailable(): Nothing =
        throw TerritoryStoreException(TerritoryFailure.NETWORK, "땅따먹기 서버가 연결되지 않았습니다.")

    override suspend fun submit(owner: TerritoryOwner, request: TerritoryCaptureRequest) =
        unavailable()

    override suspend fun cells(prefixes: List<String>, limit: Int) = unavailable()

    override suspend fun ownedCellCount(uid: String) = unavailable()

    override suspend fun topOwners(limit: Int) = unavailable()
}

/** 익명 인증이 연결되지 않은 빌드에서 쓰는 사용자. 항상 인증 실패다. */
object UnavailableTerritoryOwnerProvider : TerritoryOwnerProvider {
    override suspend fun current(): TerritoryOwner =
        throw TerritoryStoreException(TerritoryFailure.AUTH, "익명 인증이 연결되지 않았습니다.")
}
