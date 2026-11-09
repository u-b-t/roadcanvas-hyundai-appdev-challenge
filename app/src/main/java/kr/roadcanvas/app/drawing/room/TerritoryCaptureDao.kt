package kr.roadcanvas.app.drawing.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
abstract class TerritoryCaptureDao {
    @Query("SELECT * FROM territory_capture WHERE drawing_id = :drawingId")
    abstract suspend fun findByDrawing(drawingId: Long): TerritoryCaptureEntity?

    @Query("SELECT * FROM territory_capture WHERE status = :status ORDER BY created_at")
    abstract suspend fun withStatus(status: String): List<TerritoryCaptureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(capture: TerritoryCaptureEntity)
}
