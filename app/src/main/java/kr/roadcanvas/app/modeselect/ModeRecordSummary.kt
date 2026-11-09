package kr.roadcanvas.app.modeselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.territory.TerritoryCall
import kr.roadcanvas.app.territory.TerritoryGame

const val NO_RECORD_SUMMARY = "아직 기록이 없어요"

/**
 * 메인 화면 모드 섹션에 보여줄 내 최근 기록 요약.
 * [circleBestScore]는 동그라미 최고 점수, [freeDrawingCount]와 [territoryCount]는 기기에 저장한 기록 수다.
 * [territoryCells]는 서버에서 읽은 내 칸 수이며 읽지 못했으면 null이다.
 */
data class ModeRecordSummary(
    val circleBestScore: Int? = null,
    val freeDrawingCount: Int = 0,
    val territoryCount: Int = 0,
    val territoryCells: Long? = null
)

/** [mode] 섹션에 보여줄 한 줄 요약 */
fun ModeRecordSummary.textFor(mode: DrawingMode): String = when (mode) {
    DrawingMode.CIRCLE -> circleBestScore?.let { "최고 ${it}점" } ?: NO_RECORD_SUMMARY
    DrawingMode.FREE_DRAWING ->
        if (freeDrawingCount > 0) "내 그림 ${freeDrawingCount}개" else NO_RECORD_SUMMARY
    DrawingMode.TERRITORY -> when {
        // 다른 사용자에게 모두 빼앗겨도 기록이 있으면 0칸으로 보여준다.
        territoryCells != null && (territoryCells > 0 || territoryCount > 0) ->
            "내 땅 ${territoryCells}칸"
        territoryCount > 0 -> "기록 ${territoryCount}개"
        else -> NO_RECORD_SUMMARY
    }
}

/**
 * 기기 저장소와 서버 땅따먹기에서 모드별 요약을 읽는다.
 * 기기 기록을 읽지 못하면 기록이 없는 것처럼, 서버 칸 수를 읽지 못하면 기기 기록 수만 보여준다.
 */
class ModeRecordSummaryLoader(
    private val repository: CompletedDrawingRepository,
    private val territoryGame: TerritoryGame
) {
    /** 기기 기록 요약. 서버 칸 수는 비어 있다. */
    suspend fun loadLocal(): ModeRecordSummary {
        val summaries = (repository.summaries() as? StorageResult.Success)?.value.orEmpty()
        val best = (repository.bestScore(DrawingMode.CIRCLE) as? StorageResult.Success)?.value
        return ModeRecordSummary(
            circleBestScore = best,
            freeDrawingCount = summaries.count { it.mode == DrawingMode.FREE_DRAWING },
            territoryCount = summaries.count { it.mode == DrawingMode.TERRITORY }
        )
    }

    /** 서버에서 읽은 내 칸 수. 읽지 못하면 null이다. */
    suspend fun loadTerritoryCells(): Long? =
        (territoryGame.myCellCount() as? TerritoryCall.Success)?.value
}

/** 메인 화면에 들어올 때마다 요약을 다시 읽는다. 기기 기록을 읽는 동안은 null이다. */
@Composable
fun rememberModeRecordSummary(): State<ModeRecordSummary?> {
    val container = LocalContext.current.appContainer
    val summary = remember { mutableStateOf<ModeRecordSummary?>(null) }
    LaunchedEffect(container) {
        val loader = ModeRecordSummaryLoader(
            container.completedDrawingRepository,
            container.territoryGame
        )
        // 서버 응답이 늦어도 기기 기록 요약은 먼저 보여준다.
        val local = loader.loadLocal()
        summary.value = local
        summary.value = local.copy(territoryCells = loader.loadTerritoryCells())
    }
    return summary
}
