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
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    /** 사용자가 붙인 이름. 붙이지 않았으면 null이다. 버전 3에서 추가했다. */
    val name: String? = null
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

/**
 * 목록 썸네일용 축약 경로. 목록을 불러올 때 전체 경로를 읽지 않도록 저장할 때 함께 저장한다. 버전 3에서 추가했다.
 * [seq]는 정렬 순서로만 쓴다.
 */
@Entity(
    tableName = "completed_thumbnail_point",
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
data class CompletedThumbnailPointEntity(
    @ColumnInfo(name = "drawing_id") val drawingId: Long,
    val seq: Int,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "time_millis") val timeMillis: Long
)

/** 목록 요약 조회 결과. [territoryArea]는 땅따먹기 면적 지표이며 없으면 null이다. */
data class CompletedDrawingWithThumbnail(
    @Embedded val drawing: CompletedDrawingEntity,
    @ColumnInfo(name = "territory_area") val territoryArea: Double? = null,
    @Relation(parentColumn = "id", entityColumn = "drawing_id")
    val thumbnail: List<CompletedThumbnailPointEntity>
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
    @ColumnInfo(name = "started_at") val startedAtMillis: Long,
    /** 종료했지만 완료 기록으로 저장하기 전이면 종료 시각, 기록 중이면 null이다. 버전 2에서 추가했다. */
    @ColumnInfo(name = "ended_at") val endedAtMillis: Long? = null
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

/**
 * 완료 기록의 작품 공개 기록. 완료 기록 하나에 하나이며 완료 기록을 지우면 함께 지워진다. 버전 5에서 추가했다.
 * [artworkId]는 처음 공개를 시도할 때 만든 UUID이고 [status]는 마지막 공개 상태 키다.
 */
@Entity(
    tableName = "artwork_publication",
    foreignKeys = [
        ForeignKey(
            entity = CompletedDrawingEntity::class,
            parentColumns = ["id"],
            childColumns = ["drawing_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArtworkPublicationEntity(
    @PrimaryKey @ColumnInfo(name = "drawing_id") val drawingId: Long,
    @ColumnInfo(name = "artwork_id") val artworkId: String,
    val status: String
)

/**
 * 땅따먹기 획득. 칸은 쉼표로 이은 geohash7 문자열이다. 서버에 반영한 뒤에만 칸 분류 수가 있다.
 * 완료 기록을 지워도 서버 반영을 이어가도록 외래 키를 두지 않는다.
 */
@Entity(
    tableName = "territory_capture",
    indices = [Index("drawing_id", unique = true), Index("status", "created_at")]
)
data class TerritoryCaptureEntity(
    @PrimaryKey @ColumnInfo(name = "capture_id") val captureId: String,
    @ColumnInfo(name = "drawing_id") val drawingId: Long,
    val cells: String,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    val status: String,
    @ColumnInfo(name = "new_cells") val newCells: Int?,
    @ColumnInfo(name = "stolen_cells") val stolenCells: Int?,
    @ColumnInfo(name = "own_cells") val ownCells: Int?
)
