package kr.roadcanvas.app.drawing.room

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "completed_drawing",
    indices = [Index("completed_at"), Index("mode", "score")]
)
data class CompletedDrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    @ColumnInfo(name = "completed_at") val completedAtMillis: Long,
    val score: Int,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long
)

@Entity(
    tableName = "completed_route_point",
    primaryKeys = ["drawing_id", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = CompletedDrawingEntity::class,
            parentColumns = ["id"],
            childColumns = ["drawing_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CompletedRoutePointEntity(
    @ColumnInfo(name = "drawing_id") val drawingId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "time_millis") val timeMillis: Long
)

@Entity(
    tableName = "completed_metric",
    primaryKeys = ["drawing_id", "name"],
    foreignKeys = [
        ForeignKey(
            entity = CompletedDrawingEntity::class,
            parentColumns = ["id"],
            childColumns = ["drawing_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CompletedMetricEntity(
    @ColumnInfo(name = "drawing_id") val drawingId: Long,
    val name: String,
    val value: Double
)

data class CompletedDrawingWithDetails(
    @Embedded val drawing: CompletedDrawingEntity,
    @Relation(parentColumn = "id", entityColumn = "drawing_id")
    val points: List<CompletedRoutePointEntity>,
    @Relation(parentColumn = "id", entityColumn = "drawing_id")
    val metrics: List<CompletedMetricEntity>
)

/** 진행 중인 도전은 한 행만 둔다. */
@Entity(tableName = "in_progress_drawing")
data class InProgressDrawingEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val mode: String,
    @ColumnInfo(name = "started_at") val startedAtMillis: Long
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

/** 경로 점은 한 행씩 추가해 기존 점을 다시 쓰지 않는다. [seq]가 추가한 순서다. */
@Entity(tableName = "in_progress_route_point")
data class InProgressRoutePointEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "time_millis") val timeMillis: Long
)
