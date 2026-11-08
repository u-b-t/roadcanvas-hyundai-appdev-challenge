package kr.roadcanvas.app.drawing.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class InProgressDrawingDao {
    @Query("SELECT * FROM in_progress_drawing WHERE id = ${InProgressDrawingEntity.SINGLE_ID}")
    abstract suspend fun drawing(): InProgressDrawingEntity?

    @Query("SELECT * FROM in_progress_route_point ORDER BY seq")
    abstract suspend fun points(): List<InProgressRoutePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun replaceDrawing(drawing: InProgressDrawingEntity)

    @Insert
    abstract suspend fun insertPoints(points: List<InProgressRoutePointEntity>)

    /** 바뀐 행 수를 돌려준다. 시작한 도전이 없으면 0이다. */
    @Query(
        "UPDATE in_progress_drawing SET ended_at = :endedAtMillis " +
            "WHERE id = ${InProgressDrawingEntity.SINGLE_ID}"
    )
    abstract suspend fun finish(endedAtMillis: Long): Int

    @Query("DELETE FROM in_progress_drawing")
    abstract suspend fun deleteDrawing()

    @Query("DELETE FROM in_progress_route_point")
    abstract suspend fun deletePoints()

    @Transaction
    open suspend fun start(drawing: InProgressDrawingEntity) {
        deletePoints()
        replaceDrawing(drawing)
    }

    /** 시작한 도전이 없으면 저장하지 않고 false를 돌려준다. */
    @Transaction
    open suspend fun append(points: List<InProgressRoutePointEntity>): Boolean {
        if (drawing() == null) return false
        insertPoints(points)
        return true
    }

    /** 도전과 경로를 같은 시점의 상태로 읽는다. */
    @Transaction
    open suspend fun load(): Pair<InProgressDrawingEntity, List<InProgressRoutePointEntity>>? =
        drawing()?.let { it to points() }

    @Transaction
    open suspend fun clear() {
        deletePoints()
        deleteDrawing()
    }
}
