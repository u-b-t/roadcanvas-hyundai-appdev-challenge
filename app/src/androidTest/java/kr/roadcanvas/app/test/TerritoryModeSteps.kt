package kr.roadcanvas.app.test

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.START_LABEL
import kr.roadcanvas.app.challenge.TERRITORY_PROGRESS_TITLE
import kr.roadcanvas.app.challenge.TERRITORY_READY_TITLE
import kr.roadcanvas.app.challenge.formatArea
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.TERRITORY_AREA_METRIC
import kr.roadcanvas.app.drawing.TERRITORY_PERIMETER_METRIC
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.modeselect.TERRITORY_NAME
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private val TABLE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 대체 지도 표시에서 채운 영역을 나타내는 부분 */
private const val FILLED_AREA_TEXT = "채운 영역 꼭짓점"

/**
 * 땅따먹기 화면 시나리오. 기록 흐름은 동그라미 챌린지와 같아 진행·결과 단계 일부를 [ChallengeProgressSteps]와 함께 쓴다.
 * 판정 규칙 시나리오는 [TerritorySteps]가 맡는다.
 */
class TerritoryModeSteps(
    private val ruleHolder: ComposeRuleHolder,
    private val progressSteps: ChallengeProgressSteps,
    private val mapOverlaySteps: MapOverlaySteps
) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val recorder get() = fakeContainer.challengeRecorder

    @Given("땅따먹기 진행 화면에서 기록을 시작했다")
    fun startTerritory() {
        composeRule.mainClock.autoAdvance = true
        progressSteps.openApp()
        waitForText(MODE_SELECT_TITLE)
        composeRule.onNode(hasText(TERRITORY_NAME) and hasClickAction()).performClick()
        waitForText(TERRITORY_READY_TITLE)
        // 준비 화면이 시작 조건을 구독한 뒤에 테스트 위치를 보내야 화면이 받는다.
        waitUntil { fakeContainer.locationProvider.subscriberCount.value > 0 }
        runBlocking { fakeContainer.locationProvider.sendLocation(LocationSample(0.0, 0.0, 0L)) }
        val start = hasText(START_LABEL) and hasClickAction()
        waitUntil { composeRule.onAllNodes(start).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(start).performClick()
        waitForText(TERRITORY_PROGRESS_TITLE)
        // 준비 화면의 위치 구독이 해제되고 기록 세션만 위치를 받을 때까지 기다린다.
        waitUntil {
            (recorder.state.value as? RecordingState.Recording)?.mode == DrawingMode.TERRITORY &&
                fakeContainer.locationProvider.subscriberCount.value == 1 &&
                composeRule.onAllNodesWithText(TERRITORY_READY_TITLE)
                    .fetchSemanticsNodes().isEmpty()
        }
    }

    @Given("반지름 {int}m 원을 땅따먹기로 기록하고 종료했다")
    fun finishCircle(radius: Int) {
        finish(TestPaths.circle(radius.toDouble()))
    }

    @Given("{string}를 땅따먹기로 기록하고 종료했다")
    fun finishNamedPath(name: String) {
        val path = when (name) {
            "출발점 55m 앞에서 멈춘 반지름 60m 원" -> TestPaths.openCircle(60.0, 55.0)
            "200m 직선 왕복" -> TestPaths.straightOutAndBack(200.0)
            "한 자리에서 기록한 위치 3개" -> TestPaths.stationary(3)
            else -> throw IllegalArgumentException("알 수 없는 경로: $name")
        }
        finish(path)
    }

    @Given("저장된 땅따먹기 기록이 있다")
    fun saveTerritories(table: DataTable) {
        table.asMaps().forEach { row ->
            val completedAt = LocalDateTime.parse(row.getValue("완료 일시"), TABLE_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val drawing = CompletedDrawing(
                mode = DrawingMode.TERRITORY,
                completedAtMillis = completedAt,
                path = TestPaths.circle(row.getValue("반지름(m)").toDouble())
                    .toRoutePoints(completedAt),
                score = 0,
                metrics = mapOf(
                    TERRITORY_AREA_METRIC to row.getValue("면적(m²)").toDouble(),
                    TERRITORY_PERIMETER_METRIC to row.getValue("둘레(m)").toDouble()
                ),
                distanceMeters = row.getValue("거리(m)").toDouble(),
                durationMillis = row.getValue("소요 시간(초)").toLong() * 1_000
            )
            val result = runBlocking { fakeContainer.completedDrawingRepository.save(drawing) }
            assertTrue("저장 결과: $result", result is StorageResult.Success)
        }
    }

    @Then("화면에 {string}를 포함한 안내가 표시된다")
    @Then("화면에 {string}을 포함한 안내가 표시된다")
    fun assertShowsContaining(text: String) {
        waitUntil {
            composeRule.onAllNodes(hasText(text, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("완료 기록 {int}개가 땅따먹기로 저장되어 있다")
    fun assertSavedTerritories(count: Int) {
        waitUntil { summaries().size == count }
        // 한 번만 저장되는지 확인하기 위해 잠시 뒤에도 개수가 같은지 본다.
        composeRule.waitForIdle()
        assertEquals(List(count) { DrawingMode.TERRITORY }, summaries().map { it.mode })
    }

    /** 결과 화면이 저장한 면적과 같은 값을 보여주는지 확인한다. 기대값은 저장소에서 읽는다. */
    @Then("저장한 땅따먹기 면적이 화면 왼쪽 조작 영역에 표시된다")
    fun assertSavedAreaShown() {
        waitUntil { summaries().isNotEmpty() }
        val area = requireNotNull(summaries().first().territoryAreaSquareMeters)
        // 반지름 50m 원의 넓이와 같은 정도인지 확인해 다른 값을 보여주지 않는지 본다.
        assertEquals(7_854.0, area, 100.0)
        mapOverlaySteps.assertTextInControls(formatArea(area))
    }

    @Then("지도에 차지한 영역이 채워져 표시된다")
    fun assertFilledArea() {
        waitUntil {
            composeRule.onAllNodes(hasText(FILLED_AREA_TEXT, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("지도에 채운 영역이 없다")
    fun assertNoFilledArea() {
        waitUntil {
            composeRule.onAllNodes(hasText("대체 경로 지도", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodes(hasText(FILLED_AREA_TEXT, substring = true))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    private fun finish(path: List<GeoPoint>) {
        progressSteps.finishSavedChallenge(
            path.toRoutePoints(System.currentTimeMillis() - path.size * 1_000L),
            DrawingMode.TERRITORY
        )
    }

    private fun summaries(): List<CompletedDrawingSummary> = runBlocking {
        (fakeContainer.completedDrawingRepository.summaries() as StorageResult.Success).value
    }

    private fun List<GeoPoint>.toRoutePoints(start: Long): List<RoutePoint> =
        mapIndexed { i, it -> RoutePoint(it.latitude, it.longitude, start + i * 1_000L) }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }

    private fun waitForText(text: String) {
        waitUntil { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
