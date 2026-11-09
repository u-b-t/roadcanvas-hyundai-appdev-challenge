package kr.roadcanvas.app.drawing.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kr.roadcanvas.app.drawing.TERRITORY_AREA_METRIC

@Dao
abstract class CompletedDrawingDao {
    @Insert
    abstract suspend fun insertDrawing(drawing: CompletedDrawingEntity): Long

    @Insert
    abstract suspend fun insertPoints(points: List<CompletedRoutePointEntity>)

    @Insert
    abstract suspend fun insertMetrics(metrics: List<CompletedMetricEntity>)

    @Insert
    abstract suspend fun insertThumbnail(points: List<CompletedThumbnailPointEntity>)

    /** 기록과 경로, 세부 지표, 썸네일용 축약 경로를 한 트랜잭션으로 저장한다. */
    @Transaction
    open suspend fun insert(
        drawing: CompletedDrawingEntity,
        points: (drawingId: Long) -> List<CompletedRoutePointEntity>,
        metrics: (drawingId: Long) -> List<CompletedMetricEntity>,
        thumbnail: (drawingId: Long) -> List<CompletedThumbnailPointEntity>
    ): Long {
        val id = insertDrawing(drawing)
        insertPoints(points(id))
        insertMetrics(metrics(id))
        insertThumbnail(thumbnail(id))
        return id
    }

    /** 목록 요약. 땅따먹기 면적은 세부 지표 하나만 함께 읽고 나머지 지표와 전체 경로는 읽지 않는다. */
    @Transaction
    @Query(
        "SELECT completed_drawing.*, completed_metric.value AS territory_area " +
            "FROM completed_drawing LEFT JOIN completed_metric " +
            "ON completed_metric.drawing_id = completed_drawing.id " +
            "AND completed_metric.name = '$TERRITORY_AREA_METRIC' " +
            "ORDER BY completed_at DESC, id DESC"
    )
    abstract suspend fun summaries(): List<CompletedDrawingWithThumbnail>

    @Transaction
    @Query("SELECT * FROM completed_drawing WHERE id = :id")
    abstract suspend fun find(id: Long): CompletedDrawingWithDetails?

    @Query(
        "SELECT id FROM completed_drawing " +
            "WHERE mode = :mode AND completed_at = :completedAtMillis ORDER BY id LIMIT 1"
    )
    abstract suspend fun findId(mode: String, completedAtMillis: Long): Long?

    /** 바뀐 행 수를 돌려준다. 없는 ID면 0이다. */
    @Query("UPDATE completed_drawing SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String?): Int

    @Query("DELETE FROM completed_drawing WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT MAX(score) FROM completed_drawing WHERE mode = :mode")
    abstract suspend fun bestScore(mode: String): Int?
}
