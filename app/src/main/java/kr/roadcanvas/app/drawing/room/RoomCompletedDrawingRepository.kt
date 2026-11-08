package kr.roadcanvas.app.drawing.room

import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.storageResultOf

class RoomCompletedDrawingRepository(private val dao: CompletedDrawingDao) :
    CompletedDrawingRepository {
    override suspend fun save(drawing: CompletedDrawing): StorageResult<Long> = storageResultOf {
        dao.insert(
            drawing = CompletedDrawingEntity(
                mode = drawing.mode.key,
                completedAtMillis = drawing.completedAtMillis,
                score = drawing.score,
                distanceMeters = drawing.distanceMeters,
                durationMillis = drawing.durationMillis
            ),
            points = { id ->
                drawing.path.mapIndexed { index, point ->
                    CompletedRoutePointEntity(
                        id,
                        index,
                        point.latitude,
                        point.longitude,
                        point.timeMillis
                    )
                }
            },
            metrics = { id ->
                drawing.metrics.map { (name, value) -> CompletedMetricEntity(id, name, value) }
            }
        )
    }

    override suspend fun summaries(): StorageResult<List<CompletedDrawingSummary>> =
        storageResultOf { dao.summaries().map { it.toSummary() } }

    override suspend fun find(id: Long): StorageResult<CompletedDrawing?> = storageResultOf {
        dao.find(id)?.let { details ->
            val drawing = details.drawing
            CompletedDrawing(
                id = drawing.id,
                mode = DrawingMode.fromKey(drawing.mode),
                completedAtMillis = drawing.completedAtMillis,
                path = details.points.sortedBy { it.seq }
                    .map { RoutePoint(it.latitude, it.longitude, it.timeMillis) },
                score = drawing.score,
                metrics = details.metrics.associate { it.name to it.value },
                distanceMeters = drawing.distanceMeters,
                durationMillis = drawing.durationMillis
            )
        }
    }

    override suspend fun delete(id: Long): StorageResult<Unit> = storageResultOf { dao.delete(id) }

    override suspend fun bestScore(mode: DrawingMode): StorageResult<Int?> =
        storageResultOf { dao.bestScore(mode.key) }

    private fun CompletedDrawingEntity.toSummary() = CompletedDrawingSummary(
        id = id,
        mode = DrawingMode.fromKey(mode),
        completedAtMillis = completedAtMillis,
        score = score,
        distanceMeters = distanceMeters,
        durationMillis = durationMillis
    )
}
