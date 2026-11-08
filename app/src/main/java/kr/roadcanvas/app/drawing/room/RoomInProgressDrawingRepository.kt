package kr.roadcanvas.app.drawing.room

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.InProgressDrawing
import kr.roadcanvas.app.drawing.InProgressDrawingRepository
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.storageResultOf

class RoomInProgressDrawingRepository(private val dao: InProgressDrawingDao) :
    InProgressDrawingRepository {
    override suspend fun start(mode: DrawingMode, startedAtMillis: Long): StorageResult<Unit> =
        storageResultOf {
            dao.start(InProgressDrawingEntity(mode = mode.key, startedAtMillis = startedAtMillis))
        }

    override suspend fun appendPoints(points: List<RoutePoint>): StorageResult<Unit> =
        storageResultOf {
            val appended = dao.append(
                points.map {
                    InProgressRoutePointEntity(0, it.latitude, it.longitude, it.timeMillis)
                }
            )
            check(appended) { "진행 중인 도전이 없습니다." }
        }

    override suspend fun finish(endedAtMillis: Long): StorageResult<Unit> = storageResultOf {
        check(dao.finish(endedAtMillis) > 0) { "진행 중인 도전이 없습니다." }
    }

    override suspend fun load(): StorageResult<InProgressDrawing?> = storageResultOf {
        dao.load()?.let { (drawing, points) ->
            InProgressDrawing(
                mode = DrawingMode.fromKey(drawing.mode),
                startedAtMillis = drawing.startedAtMillis,
                path = points.map { RoutePoint(it.latitude, it.longitude, it.timeMillis) },
                endedAtMillis = drawing.endedAtMillis
            )
        }
    }

    override suspend fun clear(): StorageResult<Unit> = storageResultOf { dao.clear() }
}
