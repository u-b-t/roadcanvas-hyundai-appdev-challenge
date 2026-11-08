package kr.roadcanvas.app.drawing

/** 진행 중인 도전 저장소. 한 번에 하나만 보관하며 완료 기록 목록에는 나타나지 않는다. */
interface InProgressDrawingRepository {
    /** 새 도전을 시작한다. 이전에 진행 중이던 도전은 지운다. */
    suspend fun start(mode: DrawingMode, startedAtMillis: Long): StorageResult<Unit>

    /** 기존 경로는 다시 쓰지 않고 [points]만 이어서 저장한다. 시작한 도전이 없으면 실패한다. */
    suspend fun appendPoints(points: List<RoutePoint>): StorageResult<Unit>

    /** 진행 중인 도전. 없으면 null이다. */
    suspend fun load(): StorageResult<InProgressDrawing?>

    /** 진행 중인 도전을 지운다. 완료 기록 저장 후나 포기했을 때 호출한다. */
    suspend fun clear(): StorageResult<Unit>
}
