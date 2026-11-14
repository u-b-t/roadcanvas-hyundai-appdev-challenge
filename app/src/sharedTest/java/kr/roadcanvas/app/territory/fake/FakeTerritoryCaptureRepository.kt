package kr.roadcanvas.app.territory.fake

import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.territory.CaptureSyncStatus
import kr.roadcanvas.app.territory.StoredTerritoryCapture
import kr.roadcanvas.app.territory.TerritoryCaptureRepository

/** 메모리에 보관하는 획득 저장소. [failure]를 지정하면 모든 요청이 그 오류로 실패한다. */
class FakeTerritoryCaptureRepository : TerritoryCaptureRepository {
    private val captures = linkedMapOf<String, StoredTerritoryCapture>()

    var failure: Throwable? = null

    val all: List<StoredTerritoryCapture> get() = captures.values.toList()

    override suspend fun findByDrawing(drawingId: Long) =
        result { captures.values.firstOrNull { it.drawingId == drawingId } }

    override suspend fun pending() = result {
        captures.values.filter { it.status == CaptureSyncStatus.PENDING }
            .sortedBy { it.createdAtMillis }
    }

    /** 기기 DB처럼 반영 결과는 칸 수만 보관한다. */
    override suspend fun save(capture: StoredTerritoryCapture) = result {
        // Room의 drawing_id 유일 색인과 REPLACE처럼 같은 기록의 다른 획득을 지운다.
        captures.values.removeAll {
            it.drawingId == capture.drawingId && it.captureId != capture.captureId
        }
        captures[capture.captureId] = capture.copy(outcome = capture.outcome?.countsOnly())
    }

    fun reset() {
        captures.clear()
        failure = null
    }

    private inline fun <T> result(block: () -> T): StorageResult<T> =
        failure?.let { StorageResult.Failure(it) } ?: StorageResult.Success(block())
}
