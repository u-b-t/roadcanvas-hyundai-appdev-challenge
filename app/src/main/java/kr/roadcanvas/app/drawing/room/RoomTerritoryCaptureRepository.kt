package kr.roadcanvas.app.drawing.room

import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.storageResultOf
import kr.roadcanvas.app.territory.CaptureSyncStatus
import kr.roadcanvas.app.territory.StoredTerritoryCapture
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureRepository

class RoomTerritoryCaptureRepository(private val dao: TerritoryCaptureDao) :
    TerritoryCaptureRepository {
    override suspend fun findByDrawing(drawingId: Long): StorageResult<StoredTerritoryCapture?> =
        storageResultOf { dao.findByDrawing(drawingId)?.toModel() }

    override suspend fun pending(): StorageResult<List<StoredTerritoryCapture>> =
        storageResultOf { dao.withStatus(CaptureSyncStatus.PENDING.key).map { it.toModel() } }

    /** 같은 완료 기록에 다른 획득 ID를 저장하면 고유 색인 제약으로 기존 기록을 바꾼다. */
    override suspend fun save(capture: StoredTerritoryCapture): StorageResult<Unit> =
        storageResultOf {
            dao.upsert(
                TerritoryCaptureEntity(
                    captureId = capture.captureId,
                    drawingId = capture.drawingId,
                    cells = capture.cells.joinToString(CELL_SEPARATOR),
                    createdAtMillis = capture.createdAtMillis,
                    status = capture.status.key,
                    newCells = capture.outcome?.newCells,
                    stolenCells = capture.outcome?.stolenCells,
                    ownCells = capture.outcome?.ownCells
                )
            )
        }

    private fun TerritoryCaptureEntity.toModel() = StoredTerritoryCapture(
        captureId = captureId,
        drawingId = drawingId,
        cells = cells.split(CELL_SEPARATOR),
        createdAtMillis = createdAtMillis,
        status = CaptureSyncStatus.fromKey(status),
        outcome = if (newCells != null && stolenCells != null && ownCells != null) {
            TerritoryCaptureOutcome(newCells, stolenCells, ownCells)
        } else {
            null
        }
    )

    private companion object {
        const val CELL_SEPARATOR = ","
    }
}
