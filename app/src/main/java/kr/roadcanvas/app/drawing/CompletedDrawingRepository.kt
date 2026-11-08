package kr.roadcanvas.app.drawing

/** 완료한 도전 기록 저장소 */
interface CompletedDrawingRepository {
    /** 새 기록으로 저장하고 부여한 ID를 돌려준다. */
    suspend fun save(drawing: CompletedDrawing): StorageResult<Long>

    /** 완료 일시 최신순 요약 목록 */
    suspend fun summaries(): StorageResult<List<CompletedDrawingSummary>>

    /** 경로를 포함한 기록. 없으면 null이다. */
    suspend fun find(id: Long): StorageResult<CompletedDrawing?>

    /**
     * [completedAtMillis]에 완료한 [mode] 기록의 ID. 없으면 null이다.
     * 한 번에 하나의 도전만 하므로 같은 도전을 두 번 저장하지 않도록 확인하는 데 쓴다.
     */
    suspend fun findId(mode: DrawingMode, completedAtMillis: Long): StorageResult<Long?>

    /** 기록의 이름을 [name]으로 바꾼다. null이면 이름을 지운다. 없는 ID면 실패다. */
    suspend fun rename(id: Long, name: String?): StorageResult<Unit>

    /** 기록을 삭제한다. 없는 ID여도 성공이다. */
    suspend fun delete(id: Long): StorageResult<Unit>

    /** [mode]의 최고 점수. 기록이 없으면 null이다. */
    suspend fun bestScore(mode: DrawingMode): StorageResult<Int?>
}
