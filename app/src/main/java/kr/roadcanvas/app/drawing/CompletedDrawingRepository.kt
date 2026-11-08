package kr.roadcanvas.app.drawing

/** 완료한 도전 기록 저장소 */
interface CompletedDrawingRepository {
    /** 새 기록으로 저장하고 부여한 ID를 돌려준다. */
    suspend fun save(drawing: CompletedDrawing): StorageResult<Long>

    /** 완료 일시 최신순 요약 목록 */
    suspend fun summaries(): StorageResult<List<CompletedDrawingSummary>>

    /** 경로를 포함한 기록. 없으면 null이다. */
    suspend fun find(id: Long): StorageResult<CompletedDrawing?>

    /** 기록을 삭제한다. 없는 ID여도 성공이다. */
    suspend fun delete(id: Long): StorageResult<Unit>

    /** [mode]의 최고 점수. 기록이 없으면 null이다. */
    suspend fun bestScore(mode: DrawingMode): StorageResult<Int?>
}
