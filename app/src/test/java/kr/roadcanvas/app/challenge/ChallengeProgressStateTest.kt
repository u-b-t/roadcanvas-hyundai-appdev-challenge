package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.vehicle.DrivingRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeProgressStateTest {
    private val path = listOf(RoutePoint(37.5665, 126.978, 0), RoutePoint(37.5674, 126.978, 1))
    private val recording = RecordingState.Recording(DrawingMode.CIRCLE, 10_000L, path, 100.0)

    @Test
    fun `기록 중이면 시작부터 지금까지의 경과 시간과 이동 거리를 보여준다`() {
        val state = challengeProgressOf(recording, DrivingRestriction.UNRESTRICTED, 75_000L)

        assertEquals(ChallengeProgressPhase.RECORDING, state.phase)
        assertEquals(65_000L, state.elapsedMillis)
        assertEquals(100.0, state.distanceMeters, 0.0)
        assertEquals(path, state.path)
    }

    @Test
    fun `기록 상태의 모드를 화면 상태에 넘기고 기록을 준비하는 동안에는 모드를 모른다`() {
        val free = RecordingState.Recording(DrawingMode.FREE_DRAWING, 10_000L, path, 100.0)

        assertEquals(
            DrawingMode.FREE_DRAWING,
            challengeProgressOf(free, DrivingRestriction.UNRESTRICTED, 0L).mode
        )
        assertEquals(
            DrawingMode.FREE_DRAWING,
            challengeProgressOf(
                RecordingState.Failed(IllegalStateException(), DrawingMode.FREE_DRAWING),
                DrivingRestriction.UNRESTRICTED,
                0L
            ).mode
        )
        assertNull(
            challengeProgressOf(RecordingState.Idle, DrivingRestriction.UNRESTRICTED, 0L).mode
        )
        assertEquals(FREE_DRAWING_PROGRESS_TITLE, progressTitleOf(DrawingMode.FREE_DRAWING))
        assertEquals(CIRCLE_PROGRESS_TITLE, progressTitleOf(DrawingMode.CIRCLE))
    }

    @Test
    fun `정차해 조작할 수 있을 때만 종료와 포기를 할 수 있다`() {
        val unrestricted = challengeProgressOf(recording, DrivingRestriction.UNRESTRICTED, 0L)
        val restricted = challengeProgressOf(recording, DrivingRestriction.RESTRICTED, 0L)
        val unknown = challengeProgressOf(recording, DrivingRestriction.UNKNOWN, 0L)

        assertTrue(unrestricted.canControl)
        assertNull(unrestricted.controlBlockedReason)
        assertFalse(restricted.canControl)
        assertEquals(CONTROL_RESTRICTED_REASON, restricted.controlBlockedReason)
        assertFalse(unknown.canControl)
        assertEquals(CONTROL_UNKNOWN_REASON, unknown.controlBlockedReason)
    }

    @Test
    fun `기록 중 조작할 수 없으면 주행 중 표시로 바꾸고 정차하면 되돌린다`() {
        assertFalse(challengeProgressOf(recording, DrivingRestriction.UNRESTRICTED, 0L).driving)
        assertTrue(challengeProgressOf(recording, DrivingRestriction.RESTRICTED, 0L).driving)
        assertTrue(challengeProgressOf(recording, DrivingRestriction.UNKNOWN, 0L).driving)
        assertFalse(
            challengeProgressOf(RecordingState.Idle, DrivingRestriction.RESTRICTED, 0L).driving
        )
    }

    @Test
    fun `기록한 경로로 출발점까지 거리를 계산한다`() {
        val state = challengeProgressOf(recording, DrivingRestriction.UNRESTRICTED, 0L)

        assertEquals(100.0, state.lap.distanceToStartMeters!!, 1.0)
        assertFalse(state.showsFinishGuidance)
    }

    @Test
    fun `한 바퀴를 돌아 출발점 근처에 오면 종료 안내를 보인다`() {
        val center = RoutePoint(37.5665, 126.978, 0)
        val circle = (0..60).map { i ->
            val angle = 2 * Math.PI * 0.97 * i / 60
            RoutePoint(
                center.latitude + 0.00045 * Math.sin(angle),
                center.longitude + 0.00057 * (1 - Math.cos(angle)),
                i.toLong()
            )
        }
        val lap = RecordingState.Recording(DrawingMode.CIRCLE, 0L, circle, 300.0)

        assertTrue(challengeProgressOf(lap, DrivingRestriction.RESTRICTED, 0L).showsFinishGuidance)
        assertTrue(
            challengeProgressOf(lap, DrivingRestriction.UNRESTRICTED, 0L).showsFinishGuidance
        )
    }

    @Test
    fun `자유그림은 출발점 근처로 돌아와도 동그라미 전용 정보와 종료 안내를 보이지 않는다`() {
        val circle = (0..60).map { i ->
            val angle = 2 * Math.PI * 0.97 * i / 60
            RoutePoint(
                37.5665 + 0.00045 * Math.sin(angle),
                126.978 + 0.00057 * (1 - Math.cos(angle)),
                i.toLong()
            )
        }
        val free = RecordingState.Recording(DrawingMode.FREE_DRAWING, 0L, circle, 300.0)

        val state = challengeProgressOf(free, DrivingRestriction.UNRESTRICTED, 0L)

        assertFalse(state.showsLapProgress)
        assertFalse(state.showsFinishGuidance)
        // 지도의 진행 방향 표시는 자유그림에서도 쓴다.
        assertTrue(state.lap.headingDegrees != null)
        assertTrue(
            challengeProgressOf(recording, DrivingRestriction.UNRESTRICTED, 0L).showsLapProgress
        )
    }

    @Test
    fun `기록 중이 아니면 종료와 포기를 할 수 없고 이유도 표시하지 않는다`() {
        listOf(
            RecordingState.Idle,
            RecordingState.Failed(IllegalStateException()),
            RecordingState.Finished(DrawingMode.CIRCLE, 0L, 1L, path, 100.0)
        ).forEach {
            val state = challengeProgressOf(it, DrivingRestriction.RESTRICTED, 0L)
            assertFalse(state.canControl)
            assertNull(state.controlBlockedReason)
        }
        assertEquals(
            ChallengeProgressPhase.PREPARING,
            challengeProgressOf(RecordingState.Idle, DrivingRestriction.UNRESTRICTED, 0L).phase
        )
    }

    @Test
    fun `경과 시간은 한 시간 미만이면 분과 초, 이상이면 시를 붙인다`() {
        assertEquals("00:00", formatElapsed(999L))
        assertEquals("01:05", formatElapsed(65_000L))
        assertEquals("59:59", formatElapsed(3_599_000L))
        assertEquals("1:00:01", formatElapsed(3_601_000L))
    }

    @Test
    fun `이동 거리는 1km 미만은 m, 이상은 km로 표시한다`() {
        assertEquals("0 m", formatDistance(0.2))
        assertEquals("100 m", formatDistance(100.07))
        assertEquals("999 m", formatDistance(999.4))
        assertEquals("1.00 km", formatDistance(999.6))
        assertEquals("12.35 km", formatDistance(12_345.0))
    }
}
