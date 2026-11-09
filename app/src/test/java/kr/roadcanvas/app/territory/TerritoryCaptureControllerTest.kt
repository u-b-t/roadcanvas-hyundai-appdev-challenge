package kr.roadcanvas.app.territory

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerritoryCaptureControllerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeTerritoryStore()
    private val captures = FakeTerritoryCaptureRepository()
    private val owner = FakeTerritoryOwnerProvider()
    private var now = 1_000_000L
    private var captureCount = 0
    private val game = TerritoryGame(
        store,
        captures,
        owner,
        clock = { now },
        newCaptureId = { "capture-${++captureCount}" },
        conflictRetryDelayMillis = 0
    )

    private fun drawing(path: List<GeoPoint>, mode: DrawingMode = DrawingMode.TERRITORY) =
        CompletedDrawing(
            id = 7,
            mode = mode,
            completedAtMillis = 60_000,
            path = path.mapIndexed { i, it -> RoutePoint(it.latitude, it.longitude, i * 1_000L) },
            score = 0,
            metrics = emptyMap(),
            distanceMeters = 314.0,
            durationMillis = 60_000
        )

    private val circle = drawing(TestPaths.circle(50.0))

    private val TerritorySyncUiState?.capture: TerritoryCaptureState?
        get() = (this as? TerritorySyncUiState.Capture)?.state

    @Test
    fun `차지한 땅따먹기 기록이 아니면 상태를 숨긴다`() = runTest(dispatcher) {
        val controller = TerritoryCaptureController(game, backgroundScope)

        controller.show(drawing(TestPaths.circle(50.0), DrawingMode.CIRCLE))
        assertNull(controller.state.value)

        controller.show(drawing(TestPaths.straightOutAndBack(200.0)))
        assertNull(controller.state.value)
        assertNull(controller.submit())
    }

    @Test
    fun `제출한 적 없는 기록은 미제출이고 제출하면 칸 수와 함께 반영됨이다`() = runTest(dispatcher) {
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)
        assertEquals(TerritorySyncUiState.NotSubmitted, controller.state.value)
        assertEquals("미제출", controller.state.value?.label)

        controller.submit()

        val submitted = controller.state.value.capture as TerritoryCaptureState.Submitted
        assertTrue(submitted.outcome.newCells > 0)
        assertEquals("반영됨", controller.state.value?.label)
        assertFalse(controller.state.value!!.canSubmit)
    }

    @Test
    fun `연결하지 못하면 대기이고 다시 제출하면 같은 획득 ID로 반영한다`() = runTest(dispatcher) {
        store.networkFailure = true
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)
        controller.submit()

        val pending = controller.state.value.capture as TerritoryCaptureState.Pending
        assertEquals(TerritoryFailure.NETWORK, pending.failure)
        assertEquals("대기(오프라인)", controller.state.value?.label)
        assertTrue(controller.state.value!!.canSubmit)

        store.networkFailure = false
        controller.submit()

        assertTrue(controller.state.value.capture is TerritoryCaptureState.Submitted)
        assertEquals(listOf("capture-1", "capture-1"), store.submitRequests)
    }

    @Test
    fun `익명 인증에 실패하면 실패로 보이고 다시 제출할 수 있다`() = runTest(dispatcher) {
        owner.owner = null
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)
        controller.submit()

        val pending = controller.state.value.capture as TerritoryCaptureState.Pending
        assertEquals(TerritoryFailure.AUTH, pending.failure)
        assertEquals("실패", controller.state.value?.label)
        assertTrue(controller.state.value!!.canSubmit)
    }

    @Test
    fun `기기 기록을 저장하지 못하면 실패로 보인다`() = runTest(dispatcher) {
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)
        captures.failure = IllegalStateException("저장 실패")
        controller.submit()

        assertEquals(
            TerritorySyncUiState.Failed(TerritoryFailure.STORAGE),
            controller.state.value
        )
        assertEquals("실패", controller.state.value?.label)
    }

    @Test
    fun `칸이 너무 많은 영역은 제출하지 않고 영역이 너무 크다고 보인다`() = runTest(dispatcher) {
        val square = drawing(squarePath(sideDegrees = 0.03))
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(square)

        assertEquals(
            TerritoryCaptureState.TooLarge(TERRITORY_MAX_CELLS_PER_CAPTURE),
            controller.state.value.capture
        )
        assertEquals("영역이 너무 커요", controller.state.value?.label)
        assertNull(controller.submit())
        assertTrue(store.submitRequests.isEmpty())
    }

    @Test
    fun `다른 곳에서 다시 제출해 반영되면 상태를 다시 불러온다`() = runTest(dispatcher) {
        store.networkFailure = true
        game.capture(circle.id, claimedOf(circle))
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)
        assertEquals("대기(오프라인)", controller.state.value?.label)

        store.networkFailure = false
        game.resubmitPending()

        assertTrue(controller.state.value.capture is TerritoryCaptureState.Submitted)
    }

    @Test
    fun `기한이 지난 획득은 반영하지 못함으로 보이고 다시 제출하지 않는다`() = runTest(dispatcher) {
        store.networkFailure = true
        game.capture(circle.id, claimedOf(circle))
        now += TERRITORY_SUBMIT_DEADLINE_MILLIS
        val controller = TerritoryCaptureController(game, backgroundScope)
        controller.show(circle)

        assertEquals("서버에 반영하지 못함", controller.state.value?.label)
        assertFalse(controller.state.value!!.canSubmit)
        assertNull(controller.submit())
    }

    private fun claimedOf(drawing: CompletedDrawing) = TerritoryCalculator().calculate(
        drawing.path.map { GeoPoint(it.latitude, it.longitude) }
    ) as TerritoryResult.Claimed

    /**
     * 서울시청에서 시작해 한 변 [sideDegrees]도인 정사각형을 한 변 100개 점으로 돈다.
     * 마지막 점 바로 앞 점이 출발점에서 닫힘 허용 거리 안에 있도록 점 간격을 좁힌다.
     */
    private fun squarePath(sideDegrees: Double): List<GeoPoint> {
        val corners = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, 0.0 to 0.0)
        return corners.zipWithNext().flatMap { (from, to) ->
            List(100) { i ->
                val t = i / 100.0
                GeoPoint(
                    37.5665 + (from.second + (to.second - from.second) * t) * sideDegrees,
                    126.9780 + (from.first + (to.first - from.first) * t) * sideDegrees
                )
            }
        } + GeoPoint(37.5665, 126.9780)
    }
}
