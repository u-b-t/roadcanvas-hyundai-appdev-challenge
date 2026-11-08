package kr.roadcanvas.app.drawing.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CompletedDrawingDao {
    @Insert
    abstract suspend fun insertDrawing(drawing: CompletedDrawingEntity): Long

    @Insert
    abstract suspend fun insertPoints(points: List<CompletedRoutePointEntity>)

    @Insert
    abstract suspend fun insertMetrics(metrics: List<CompletedMetricEntity>)

    /** 기록과 경로, 세부 지표를 한 트랜잭션으로 저장한다. */
    @Transaction
    open suspend fun insert(
        drawing: CompletedDrawingEntity,
        points: (drawingId: Long) -> List<CompletedRoutePointEntity>,
        metrics: (drawingId: Long) -> List<CompletedMetricEntity>
    ): Long {
        val id = insertDrawing(drawing)
        insertPoints(points(id))
        insertMetrics(metrics(id))
        return id
    }

    @Query("SELECT * FROM completed_drawing ORDER BY completed_at DESC, id DESC")
    abstract suspend fun summaries(): List<CompletedDrawingEntity>

    @Transaction
    @Query("SELECT * FROM completed_drawing WHERE id = :id")
    abstract suspend fun find(id: Long): CompletedDrawingWithDetails?

    @Query(
        "SELECT id FROM completed_drawing " +
            "WHERE mode = :mode AND completed_at = :completedAtMillis ORDER BY id LIMIT 1"
    )
    abstract suspend fun findId(mode: String, completedAtMillis: Long): Long?

    @Query("DELETE FROM completed_drawing WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT MAX(score) FROM completed_drawing WHERE mode = :mode")
    abstract suspend fun bestScore(mode: String): Int?
}
