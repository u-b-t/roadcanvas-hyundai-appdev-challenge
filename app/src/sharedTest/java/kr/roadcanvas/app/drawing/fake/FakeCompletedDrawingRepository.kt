package kr.roadcanvas.app.drawing.fake

import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.routeThumbnail

/** 메모리에 보관하는 완료 기록 저장소. [failure]를 지정하면 모든 요청이 그 오류로 실패한다. */
class FakeCompletedDrawingRepository : CompletedDrawingRepository {
    private val drawings = mutableMapOf<Long, CompletedDrawing>()
    private var nextId = 1L

    var failure: Throwable? = null

    override suspend fun save(drawing: CompletedDrawing) = result {
        val id = nextId++
        drawings[id] = drawing.copy(id = id)
        id
    }

    override suspend fun summaries() = result {
        drawings.values
            .sortedWith(
                compareByDescending<CompletedDrawing> {
                    it.completedAtMillis
                }.thenByDescending { it.id }
            )
            .map {
                CompletedDrawingSummary(
                    it.id,
                    it.mode,
                    it.completedAtMillis,
                    it.score,
                    it.distanceMeters,
                    it.durationMillis,
                    it.name,
                    routeThumbnail(it.path)
                )
            }
    }

    override suspend fun find(id: Long) = result { drawings[id] }

    override suspend fun findId(mode: DrawingMode, completedAtMillis: Long) = result {
        drawings.values.filter { it.mode == mode && it.completedAtMillis == completedAtMillis }
            .minOfOrNull { it.id }
    }

    override suspend fun rename(id: Long, name: String?): StorageResult<Unit> {
        val drawing = drawings[id]
            ?: return failure?.let { StorageResult.Failure(it) }
                ?: StorageResult.Failure(IllegalStateException("완료 기록이 없습니다."))
        return result { drawings[id] = drawing.copy(name = name) }
    }

    override suspend fun delete(id: Long) = result<Unit> { drawings.remove(id) }

    override suspend fun bestScore(mode: DrawingMode) =
        result { drawings.values.filter { it.mode == mode }.maxOfOrNull { it.score } }

    fun reset() {
        drawings.clear()
        nextId = 1L
        failure = null
    }

    private inline fun <T> result(block: () -> T): StorageResult<T> =
        failure?.let { StorageResult.Failure(it) } ?: StorageResult.Success(block())
}
