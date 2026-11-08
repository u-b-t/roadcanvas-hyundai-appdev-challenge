package kr.roadcanvas.app.drawing.fake

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.InProgressDrawing
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult

/** 메모리에 보관하는 진행 중 기록 저장소. [failure]를 지정하면 모든 요청이 그 오류로 실패한다. */
class FakeInProgressDrawingRepository : InProgressDrawingRepository {
    private var drawing: InProgressDrawing? = null

    var failure: Throwable? = null

    override suspend fun start(mode: DrawingMode, startedAtMillis: Long) = result {
        drawing = InProgressDrawing(mode, startedAtMillis, emptyList())
    }

    override suspend fun appendPoints(points: List<RoutePoint>) = result {
        val current = checkNotNull(drawing) { "진행 중인 도전이 없습니다." }
        drawing = current.copy(path = current.path + points)
    }

    override suspend fun load() = result { drawing }

    override suspend fun clear() = result { drawing = null }

    fun reset() {
        drawing = null
        failure = null
    }

    private inline fun <T> result(block: () -> T): StorageResult<T> {
        failure?.let { return StorageResult.Failure(it) }
        return try {
            StorageResult.Success(block())
        } catch (e: IllegalStateException) {
            StorageResult.Failure(e)
        }
    }
}
