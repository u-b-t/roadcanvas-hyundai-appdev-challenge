package kr.roadcanvas.app.territory.fake

import kr.roadcanvas.app.account.defaultNickname
import kr.roadcanvas.app.territory.StoredTerritoryCell
import kr.roadcanvas.app.territory.StoredTerritoryOwner
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureRequest
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGrid
import kr.roadcanvas.app.territory.TerritoryOwner
import kr.roadcanvas.app.territory.TerritoryStore
import kr.roadcanvas.app.territory.TerritoryStoreException
import kr.roadcanvas.app.territory.territoryCaptureOutcomeOf

/** 서버에 반영된 획득 기록 */
data class FakeTerritoryCapture(
    val ownerUid: String,
    val cells: List<String>,
    val outcome: TerritoryCaptureOutcome
)

/**
 * 메모리에 칸 소유자, 획득 기록, 순위 문서를 보관하는 서버 저장소. 서버 트랜잭션처럼 획득 기록을 먼저 보고
 * 이미 있으면 아무것도 바꾸지 않고, 없으면 칸 소유자를 덮어쓰고 보유 칸 수를 늘리고 줄인다.
 *
 * 실패 주입:
 * - [networkFailure]: 모든 요청이 연결 실패한다.
 * - [conflictsBeforeSuccess]: 다음 제출부터 이 횟수만큼 경합 거부(아무것도 쓰지 않음)한다.
 * - [submitFailure]: 지정하면 제출이 그 이유로 계속 실패한다.
 * - [loseNextSubmitResponse]: 다음 제출은 서버에 반영된 뒤 응답만 연결 실패한다.
 */
class FakeTerritoryStore : TerritoryStore {
    private val cellOwners = linkedMapOf<String, String>()
    private val owners = linkedMapOf<String, StoredTerritoryOwner>()
    private val captures = linkedMapOf<String, FakeTerritoryCapture>()

    var networkFailure: Boolean = false
    var conflictsBeforeSuccess: Int = 0
    var submitFailure: TerritoryFailure? = null
    var loseNextSubmitResponse: Boolean = false

    /** 지금까지 받은 요청. `이름:인자` 형식이다. */
    val requests = mutableListOf<String>()

    /** 지금까지 받은 제출의 획득 ID. 다시 실행하면 같은 ID가 반복된다. */
    val submitRequests: List<String>
        get() = requests.filter { it.startsWith("submit:") }.map { it.substringAfter(':') }

    val cells: Map<String, String> get() = cellOwners.toMap()

    fun capture(captureId: String): FakeTerritoryCapture? = captures[captureId]

    fun owner(uid: String): StoredTerritoryOwner? = owners[uid]

    /** 다른 사용자가 이미 소유한 칸을 서버에 둔다. 순위 문서의 칸 수도 맞춘다. */
    fun putCells(ownerUid: String, nickname: String, cells: Collection<String>) {
        cells.forEach { cell -> cellOwners.put(cell, ownerUid)?.let { adjust(it, null, -1) } }
        adjust(ownerUid, nickname, cells.size.toLong())
    }

    override suspend fun submit(
        owner: TerritoryOwner,
        request: TerritoryCaptureRequest
    ): TerritoryCaptureOutcome {
        request("submit", request.captureId)
        submitFailure?.let { throw TerritoryStoreException(it, "서버가 제출을 거부했습니다.") }
        if (conflictsBeforeSuccess > 0) {
            conflictsBeforeSuccess--
            throw TerritoryStoreException(TerritoryFailure.CONFLICT, "동시 획득과 겹쳤습니다.")
        }
        captures[request.captureId]?.let { existing ->
            if (existing.ownerUid != owner.uid) denied()
            // 서버 획득 기록에는 칸 수만 남는다.
            return existing.outcome.countsOnly()
        }
        val previousOwners = request.cells.associateWith { cellOwners[it] }
        val outcome = territoryCaptureOutcomeOf(owner.uid, previousOwners) { uid ->
            owners[uid]?.nickname ?: defaultNickname(uid)
        }
        previousOwners.values.filterNotNull()
            .filter { it != owner.uid }
            .forEach { adjust(it, null, -1) }
        request.cells.forEach { cellOwners[it] = owner.uid }
        adjust(owner.uid, owner.nickname, (outcome.newCells + outcome.stolenCells).toLong())
        captures[request.captureId] = FakeTerritoryCapture(owner.uid, request.cells, outcome)
        if (loseNextSubmitResponse) {
            loseNextSubmitResponse = false
            throw TerritoryStoreException(TerritoryFailure.NETWORK, "응답을 받지 못했습니다.")
        }
        return outcome
    }

    override suspend fun cells(regions: List<String>, limit: Int): List<StoredTerritoryCell> {
        request("cells", regions.joinToString(","))
        return cellOwners.filterKeys { TerritoryGrid.parse(it)?.regionId in regions }
            .map { (id, owner) -> StoredTerritoryCell(id, owner) }
            .take(limit)
    }

    override suspend fun ownedCellCount(uid: String): Long {
        request("ownedCellCount", uid)
        return owners[uid]?.cellCount ?: 0
    }

    override suspend fun topOwners(limit: Int): List<StoredTerritoryOwner> {
        request("topOwners", limit.toString())
        return owners.values.sortedByDescending { it.cellCount }.take(limit)
    }

    fun reset() {
        cellOwners.clear()
        owners.clear()
        captures.clear()
        networkFailure = false
        conflictsBeforeSuccess = 0
        submitFailure = null
        loseNextSubmitResponse = false
        requests.clear()
    }

    private fun adjust(uid: String, nickname: String?, delta: Long) {
        val current = owners[uid]
        owners[uid] = StoredTerritoryOwner(
            uid = uid,
            nickname = nickname ?: current?.nickname ?: uid,
            cellCount = ((current?.cellCount ?: 0) + delta).coerceAtLeast(0)
        )
    }

    private fun request(name: String, argument: String) {
        requests += "$name:$argument"
        if (networkFailure) throw TerritoryStoreException(TerritoryFailure.NETWORK, "연결 실패")
    }

    private fun denied(): Nothing =
        throw TerritoryStoreException(TerritoryFailure.UNKNOWN, "서버 규칙이 요청을 거부했습니다.")
}
