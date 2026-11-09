package kr.roadcanvas.app.territory

import kr.roadcanvas.app.drawing.StorageResult

/** 기기에 보관한 획득의 서버 반영 상태 */
enum class CaptureSyncStatus(val key: String) {
    /** 아직 서버에 반영하지 못했다. 연결되면 같은 획득 ID로 다시 제출한다. */
    PENDING("pending"),

    /** 서버에 반영했다. */
    SUBMITTED("submitted"),

    /** 만든 지 [TERRITORY_SUBMIT_DEADLINE_MILLIS]가 지나 서버 반영을 포기했다. */
    EXPIRED("expired");

    companion object {
        fun fromKey(key: String): CaptureSyncStatus = entries.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("알 수 없는 획득 상태: $key")
    }
}

/**
 * 완료 기록 [drawingId]에서 만든 획득. [captureId]와 [cells]는 처음 만들 때 정하고 다시 제출해도 바꾸지 않는다.
 * [outcome]은 서버에 반영한 뒤에만 있다. 시각은 epoch 밀리초다.
 */
data class StoredTerritoryCapture(
    val captureId: String,
    val drawingId: Long,
    val cells: List<String>,
    val createdAtMillis: Long,
    val status: CaptureSyncStatus,
    val outcome: TerritoryCaptureOutcome? = null
)

/** 획득을 기기에 보관하는 저장소. 완료 기록을 지워도 서버 반영은 이어서 하도록 함께 지우지 않는다. */
interface TerritoryCaptureRepository {
    /** [drawingId]에서 만든 획득. 없으면 null이다. */
    suspend fun findByDrawing(drawingId: Long): StorageResult<StoredTerritoryCapture?>

    /** 서버에 반영하지 못한 획득. 만든 순서다. */
    suspend fun pending(): StorageResult<List<StoredTerritoryCapture>>

    /** 획득을 저장한다. 같은 [StoredTerritoryCapture.captureId]는 바꾼다. */
    suspend fun save(capture: StoredTerritoryCapture): StorageResult<Unit>
}
