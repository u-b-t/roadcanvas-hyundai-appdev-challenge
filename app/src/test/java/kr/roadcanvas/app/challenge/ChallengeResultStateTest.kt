package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.territoryAreaSquareMeters
import kr.roadcanvas.app.drawing.territoryPerimeterMeters
import kr.roadcanvas.app.score.CircleScoreCalculator
import kr.roadcanvas.app.score.CircleScoreConfig
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.score.ZeroScoreReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        assertTrue(result.score!!.score >= 90)
        assertEquals(90_000L, result.durationMillis)
        assertEquals(START + 90_000, result.completedAtMillis)
        // 반지름 50m 원의 둘레
        assertEquals(314.0, result.distanceMeters, 2.0)
        val drawing = result.toCompletedDrawing()
        assertEquals(result.score!!.score, drawing.score)
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

        assertEquals(ZeroScoreReason.NO_MOVEMENT, result.score!!.zeroReason)
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
        assertEquals(ZeroScoreReason.INSUFFICIENT_POINTS, result.score!!.zeroReason)
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

    @Test
    fun `점수 구성은 원형도, 출발·도착 근접도, 회전 완성도 순서다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        assertEquals(
            listOf("원형도", "출발·도착 근접도", "회전 완성도"),
            result.scoreComponents.map { it.label }
        )
        assertTrue(result.scoreComponents.all { it.percent in 90..100 })
        assertNull(result.improvementTip)
    }

    @Test
    fun `반원은 출발점으로 돌아오지 않아 가장 낮은 항목의 고칠 점을 안내한다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.semicircle(50.0)),
            START,
            START + 90_000
        )

        val lowest = result.scoreComponents.minBy { it.ratio }
        assertEquals("출발·도착 근접도", lowest.label)
        assertEquals("출발점으로 돌아와 끝내 보세요", result.improvementTip)
    }

    @Test
    fun `맞춤 원은 점수 계산에 쓴 중심과 반지름이다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        val circle = result.fitCircle!!
        val metrics = result.score!!.metrics!!
        assertEquals(metrics.radiusMeters, circle.radiusMeters, 0.0)
        assertEquals(metrics.center.latitude, circle.center.latitude, 0.0)
        assertEquals(metrics.center.longitude, circle.center.longitude, 0.0)
    }

    @Test
    fun `0점이면 점수 구성, 고칠 점, 맞춤 원이 없다`() {
        val result = challengeResultOf(
            DrawingMode.CIRCLE,
            path(TestPaths.stationary(30)),
            START,
            START + 30_000
        )

        assertTrue(result.scoreComponents.isEmpty())
        assertNull(result.improvementTip)
        assertNull(result.fitCircle)
    }

    @Test
    fun `최고 기록과 비교한 결과와 안내`() {
        assertEquals(BestRecordComparison.FirstRecord, bestRecordComparisonOf(50, null))
        assertEquals(BestRecordComparison.NewRecord(80, 8), bestRecordComparisonOf(88, 80))
        assertEquals(BestRecordComparison.TiedBest(80), bestRecordComparisonOf(80, 80))
        assertEquals(BestRecordComparison.BelowBest(90, 15), bestRecordComparisonOf(75, 90))
        assertEquals("첫 기록이에요", BestRecordComparison.FirstRecord.text)
        assertEquals("신기록! 이전 최고 80점보다 8점 높아요", bestRecordComparisonOf(88, 80).text)
        assertEquals("최고 기록 80점과 같아요", bestRecordComparisonOf(80, 80).text)
        assertEquals("최고 기록 90점까지 15점 남았어요", bestRecordComparisonOf(75, 90).text)
    }

    @Test
    fun `자유그림은 점수를 계산하지 않고 점수 0과 빈 세부 지표로 저장한다`() {
        val result = challengeResultOf(
            DrawingMode.FREE_DRAWING,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        assertNull(result.score)
        assertNull(result.zeroReasonText)
        assertTrue(result.savable)
        assertEquals(314.0, result.distanceMeters, 2.0)
        val drawing = result.toCompletedDrawing()
        assertEquals(DrawingMode.FREE_DRAWING, drawing.mode)
        assertEquals(0, drawing.score)
        assertEquals(emptyMap<String, Double>(), drawing.metrics)
        assertEquals(90_000L, drawing.durationMillis)
    }

    @Test
    fun `이동 거리가 기준보다 짧은 자유그림은 저장하지 않는다`() {
        val stationary = challengeResultOf(
            DrawingMode.FREE_DRAWING,
            path(TestPaths.stationary(30)),
            START,
            START + 30_000
        )
        val empty = challengeResultOf(DrawingMode.FREE_DRAWING, emptyList(), START, START + 1)

        assertFalse(stationary.savable)
        assertFalse(empty.savable)
    }

    @Test
    fun `동그라미 챌린지는 위치가 부족하거나 움직이지 않은 도전만 저장하지 않는다`() {
        fun savable(points: List<GeoPoint>) =
            challengeResultOf(DrawingMode.CIRCLE, path(points), START, START + 60_000).savable

        assertFalse(savable(TestPaths.stationary(3)))
        assertFalse(savable(TestPaths.stationary(30)))
        assertTrue(savable(TestPaths.straightOutAndBack(100.0)))
        assertTrue(savable(TestPaths.circle(50.0)))
    }

    @Test
    fun `저장한 자유그림을 다시 보여줄 때도 점수를 계산하지 않는다`() {
        val drawing = challengeResultOf(
            DrawingMode.FREE_DRAWING,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        ).toCompletedDrawing()

        val result = challengeResultOf(drawing)

        assertEquals(DrawingMode.FREE_DRAWING, result.mode)
        assertNull(result.score)
        assertEquals(drawing.distanceMeters, result.distanceMeters, 0.0)
    }

    @Test
    fun `자유그림은 점수 구성, 고칠 점, 맞춤 원이 없다`() {
        val result = challengeResultOf(
            DrawingMode.FREE_DRAWING,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        assertTrue(result.scoreComponents.isEmpty())
        assertNull(result.improvementTip)
        assertNull(result.fitCircle)
    }

    @Test
    fun `땅따먹기는 닫힌 경로의 면적과 둘레를 판정하고 세부 지표로 저장한다`() {
        val result = challengeResultOf(
            DrawingMode.TERRITORY,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        val claimed = result.territory as TerritoryResult.Claimed
        assertTrue(result.savable)
        assertNull(result.score)
        // 반지름 50m 원의 넓이와 둘레
        assertEquals(7854.0, claimed.areaSquareMeters, 80.0)
        val drawing = result.toCompletedDrawing()
        assertEquals(0, drawing.score)
        assertEquals(claimed.areaSquareMeters, drawing.territoryAreaSquareMeters!!, 0.0)
        assertEquals(claimed.perimeterMeters, drawing.territoryPerimeterMeters!!, 0.0)
        assertEquals(314.0, drawing.territoryPerimeterMeters!!, 3.0)
    }

    @Test
    fun `저장한 땅따먹기 기록은 같은 경로로 영역 경계를 다시 구한다`() {
        val result = challengeResultOf(
            DrawingMode.TERRITORY,
            path(TestPaths.circle(50.0)),
            START,
            START + 90_000
        )

        val boundary = territoryBoundaryOf(result.toCompletedDrawing())

        assertEquals((result.territory as TerritoryResult.Claimed).boundary, boundary)
        assertNull(
            territoryBoundaryOf(result.copy(mode = DrawingMode.FREE_DRAWING).toCompletedDrawing())
        )
    }

    @Test
    fun `영역을 차지하지 못한 땅따먹기는 이동했어도 저장하지 않는다`() {
        val notClosed = challengeResultOf(
            DrawingMode.TERRITORY,
            path(TestPaths.openCircle(60.0, 55.0)),
            START,
            START + 90_000
        )
        val tooSmall = challengeResultOf(
            DrawingMode.TERRITORY,
            path(TestPaths.straightOutAndBack(200.0)),
            START,
            START + 90_000
        )
        val insufficient = challengeResultOf(
            DrawingMode.TERRITORY,
            path(TestPaths.stationary(30)),
            START,
            START + 30_000
        )

        assertTrue(notClosed.territory is TerritoryResult.NotClosed)
        assertTrue(tooSmall.territory is TerritoryResult.TooSmall)
        assertEquals(TerritoryResult.InsufficientPoints, insufficient.territory)
        assertTrue(notClosed.distanceMeters > FREE_DRAWING_MIN_DISTANCE_METERS)
        assertFalse(notClosed.savable)
        assertFalse(tooSmall.savable)
        assertFalse(insufficient.savable)
    }

    @Test
    fun `땅따먹기가 아닌 모드는 영역을 판정하지 않는다`() {
        val circle =
            challengeResultOf(DrawingMode.CIRCLE, path(TestPaths.circle(50.0)), START, START)
        val free = challengeResultOf(
            DrawingMode.FREE_DRAWING,
            path(TestPaths.circle(50.0)),
            START,
            START
        )

        assertNull(circle.territory)
        assertNull(free.territory)
        assertNull(free.toCompletedDrawing().territoryAreaSquareMeters)
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
