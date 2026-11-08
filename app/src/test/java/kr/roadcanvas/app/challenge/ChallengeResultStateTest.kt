package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.CircleScoreConfig
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.score.ZeroScoreReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeResultStateTest {
    private fun path(points: List<GeoPoint>) =
        points.mapIndexed { i, it -> RoutePoint(it.latitude, it.longitude, START + i * 1_000L) }

    @Test
    fun `원 경로는 점수와 세부 지표, 이동 거리와 소요 시간을 함께 계산한다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        assertTrue(result.score.score >= 90)
        assertEquals(90_000L, result.durationMillis)
        assertEquals(START + 90_000, result.completedAtMillis)
        // 반지름 50m 원의 둘레
        assertEquals(314.0, result.distanceMeters, 2.0)
        val drawing = result.toCompletedDrawing()
        assertEquals(result.score.score, drawing.score)
        assertEquals(8, drawing.metrics.size)
        assertEquals(50.0, drawing.metrics.getValue("radiusMeters"), 1.0)
    }

    @Test
    fun `0점이면 세부 지표 없이 저장한다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.stationary(30)),
            START,
            START + 30_000
        )

        assertEquals(ZeroScoreReason.NO_MOVEMENT, result.score.zeroReason)
        assertEquals("이동 거리가 50m보다 짧아요", result.zeroReasonText)
        assertEquals(emptyMap<String, Double>(), result.toCompletedDrawing().metrics)
    }

    @Test
    fun `0점 사유 안내는 점수를 계산한 설정의 기준값을 쓴다`() {
        val calculator = CircleScoreCalculator(CircleScoreConfig(minValidPoints = 40))

        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.stationary(30)),
            START,
            START + 30_000,
            calculator
        )

        assertEquals("기록한 위치가 40개보다 적어요", result.zeroReasonText)
    }

    @Test
    fun `0점이 아니면 사유 안내가 없다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        assertEquals(null, result.zeroReasonText)
    }

    @Test
    fun `저장한 완료 기록으로 같은 결과를 다시 만든다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.straightOutAndBack(100.0)),
            START,
            START + 60_000
        )

        assertEquals(result, challengeResultOf(result.toCompletedDrawing()))
    }

    @Test
    fun `종료 시각이 시작보다 이르면 소요 시간은 0이다`() {
        val result = challengeResultOf(DrawingMode.CIRCLE, emptyList(), START, START - 1)

        assertEquals(0L, result.durationMillis)
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, result.score.zeroReason)
    }

    @Test
    fun `0점 사유 안내에 점수 계산 기준값을 넣는다`() {
        val config = CircleScoreConfig(
            minValidPoints = 12,
            minPathLengthMeters = 40.0,
            minDiameterMeters = 25.0
        )

        assertEquals(
            "기록한 위치가 12개보다 적어요",
            zeroScoreReasonText(ZeroScoreReason.INSUFFICIENT_POINTS, config)
        )
        assertEquals(
            "이동 거리가 40m보다 짧아요",
            zeroScoreReasonText(ZeroScoreReason.NO_MOVEMENT, config)
        )
        assertEquals(
            "원의 지름이 25m보다 작아요",
            zeroScoreReasonText(ZeroScoreReason.TOO_SMALL, config)
        )
        assertEquals(
            "경로가 직선에 가까워 원으로 볼 수 없어요",
            zeroScoreReasonText(ZeroScoreReason.NOT_CIRCULAR, config)
        )
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
