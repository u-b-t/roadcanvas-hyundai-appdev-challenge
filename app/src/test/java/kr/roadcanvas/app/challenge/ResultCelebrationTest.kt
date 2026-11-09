package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritorySyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultCelebrationTest {
    private val circle = challengeResultOf(
        DrawingMode.CIRCLE,
        TestPaths.circle(50.0).mapIndexed { i, it ->
            RoutePoint(it.latitude, it.longitude, i * 1_000L)
        },
        0L,
        90_000L
    )

    private fun calculated(comparison: BestRecordComparison?) = ChallengeResultUiState.Calculated(
        circle,
        ResultSaveStatus.SAVED,
        bestComparison = comparison
    )

    @Test
    fun `최고 기록을 넘거나 첫 기록이면 최고 기록이다`() {
        assertTrue(isBestRecord(90, BestRecordComparison.NewRecord(50, 40)))
        assertTrue(isBestRecord(90, BestRecordComparison.FirstRecord))
        assertFalse(isBestRecord(90, BestRecordComparison.TiedBest(90)))
        assertFalse(isBestRecord(40, BestRecordComparison.BelowBest(90, 50)))
        assertFalse(isBestRecord(90, null))
    }

    @Test
    fun `0점은 첫 기록이어도 최고 기록이 아니다`() {
        assertFalse(isBestRecord(0, BestRecordComparison.FirstRecord))
    }

    @Test
    fun `동그라미는 최고 기록을 경신했을 때만 연출한다`() {
        assertEquals(
            NEW_BEST_CELEBRATION,
            circleCelebrationOf(calculated(BestRecordComparison.NewRecord(50, 40)))
        )
        assertNull(circleCelebrationOf(calculated(BestRecordComparison.BelowBest(100, 10))))
        assertNull(circleCelebrationOf(ChallengeResultUiState.Calculating))
    }

    @Test
    fun `점수가 없는 모드는 최고 기록 연출을 하지 않는다`() {
        val free = circle.copy(mode = DrawingMode.FREE_DRAWING, score = null)

        assertNull(
            circleCelebrationOf(
                ChallengeResultUiState.Calculated(
                    free,
                    ResultSaveStatus.SAVED,
                    bestComparison = BestRecordComparison.FirstRecord
                )
            )
        )
    }

    private fun submitted(new: Int, stolen: Int, own: Int) = TerritorySyncUiState.Capture(
        TerritoryCaptureState.Submitted(TerritoryCaptureOutcome(new, stolen, own))
    )

    @Test
    fun `땅따먹기는 빼앗은 칸이 있으면 빼앗은 칸 수를 알린다`() {
        assertEquals("3칸을 빼앗았어요", territoryCelebrationOf(submitted(5, 3, 1)))
    }

    @Test
    fun `빼앗은 칸 없이 새 칸만 있으면 새 칸 수를 알린다`() {
        assertEquals("5칸을 새로 차지했어요", territoryCelebrationOf(submitted(5, 0, 1)))
    }

    @Test
    fun `이미 내 칸뿐이거나 반영되지 않았으면 연출하지 않는다`() {
        assertNull(territoryCelebrationOf(submitted(0, 0, 4)))
        assertNull(territoryCelebrationOf(null))
        assertNull(territoryCelebrationOf(TerritorySyncUiState.Loading))
        assertNull(
            territoryCelebrationOf(
                TerritorySyncUiState.Capture(
                    TerritoryCaptureState.Pending(4, TerritoryFailure.NETWORK)
                )
            )
        )
    }
}
