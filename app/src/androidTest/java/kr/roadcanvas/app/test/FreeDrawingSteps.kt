package kr.roadcanvas.app.test

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.FREE_DRAWING_PROGRESS_TITLE
import kr.roadcanvas.app.challenge.FREE_DRAWING_READY_TITLE
import kr.roadcanvas.app.challenge.START_LABEL
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.modeselect.HOME_SCREEN_TAG
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_ITEM_SCORE_TAG
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_LIST_TAG
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.navigation.Routes
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private val TABLE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 자유그림 시나리오. 기록 흐름은 동그라미 챌린지와 같아 진행·결과 단계 일부를 [ChallengeProgressSteps]와 함께 쓴다.
 * 준비 화면은 내비게이션으로 바로 연다.
 */
class FreeDrawingSteps(
    private val ruleHolder: ComposeRuleHolder,
    private val progressSteps: ChallengeProgressSteps
) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val recorder get() = fakeContainer.challengeRecorder

    @Given("자유그림 준비 화면을 연다")
    fun openFreeDrawingReady() {
        composeRule.mainClock.autoAdvance = true
        lateinit var navController: NavHostController
        TestScreens.launchContent {
            navController = rememberNavController()
            RoadCanvasNavHost(navController = navController, splashDurationMillis = 0L)
        }
        // 실제 앱과 같은 뒤로 가기 흐름을 위해 메인 화면 위에 연다.
        composeRule.waitForHome()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            navController.navigate(Routes.FREE_READY)
        }
        waitForText(FREE_DRAWING_READY_TITLE)
        // 준비 화면이 시작 조건을 구독한 뒤에 테스트 위치를 보내야 화면이 받는다.
        waitUntil { fakeContainer.locationProvider.subscriberCount.value > 0 }
    }

    @Given("자유그림 진행 화면에서 기록을 시작했다")
    fun startFreeDrawing() {
        openFreeDrawingReady()
        runBlocking { fakeContainer.locationProvider.sendLocation(LocationSample(0.0, 0.0, 0L)) }
        val start = hasText(START_LABEL) and hasClickAction()
        waitUntil { composeRule.onAllNodes(start).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(start).performClick()
        waitForText(FREE_DRAWING_PROGRESS_TITLE)
        // 준비 화면의 위치 구독이 해제되고 기록 세션만 위치를 받을 때까지 기다린다.
        waitUntil {
            (recorder.state.value as? RecordingState.Recording)?.mode == DrawingMode.FREE_DRAWING &&
                fakeContainer.locationProvider.subscriberCount.value == 1 &&
                composeRule.onAllNodesWithText(FREE_DRAWING_READY_TITLE)
                    .fetchSemanticsNodes().isEmpty()
        }
    }

    @Given("경로 점 {int}개를 기록한 진행 중인 자유그림이 저장되어 있다")
    fun saveInProgress(count: Int) {
        progressSteps.saveInProgress(count, DrawingMode.FREE_DRAWING)
    }

    @Given("반지름 {int}m 원을 자유그림으로 기록하고 종료했다")
    fun finishCircle(radius: Int) {
        progressSteps.finishSavedChallenge(
            TestPaths.circle(radius.toDouble()).toRoutePoints(),
            DrawingMode.FREE_DRAWING
        )
    }

    @Given("한 자리에서 위치 {int}개를 자유그림으로 기록하고 종료했다")
    fun finishStationary(count: Int) {
        progressSteps.finishSavedChallenge(
            TestPaths.stationary(count).toRoutePoints(),
            DrawingMode.FREE_DRAWING
        )
    }

    @Given("저장된 자유그림 기록이 있다")
    fun saveFreeDrawings(table: DataTable) {
        table.asMaps().forEach { row ->
            val completedAt = LocalDateTime.parse(row.getValue("완료 일시"), TABLE_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val pointCount = row.getValue("경로 점").toInt()
            val drawing = CompletedDrawing(
                mode = DrawingMode.FREE_DRAWING,
                completedAtMillis = completedAt,
                path = List(pointCount) { index ->
                    RoutePoint(37.5665 + index * 0.0001, 126.978, completedAt + index * 1_000L)
                },
                score = 0,
                metrics = emptyMap(),
                distanceMeters = row.getValue("거리(m)").toDouble(),
                durationMillis = row.getValue("소요 시간(초)").toLong() * 1_000
            )
            val result = runBlocking { fakeContainer.completedDrawingRepository.save(drawing) }
            assertTrue("저장 결과: $result", result is StorageResult.Success)
        }
    }

    @Then("완료 기록 {int}개가 자유그림으로 저장되어 있다")
    fun assertSavedFreeDrawings(count: Int) {
        waitUntil { summaries().size == count }
        // 한 번만 저장되는지 확인하기 위해 잠시 뒤에도 개수가 같은지 본다.
        composeRule.waitForIdle()
        val modes = summaries().map { it.mode }
        assertEquals(List(count) { DrawingMode.FREE_DRAWING }, modes)
    }

    @Then("목록에 점수가 {int}개 표시된다")
    fun assertListScoreCount(count: Int) {
        val scores = hasTestTag(MY_DRAWINGS_ITEM_SCORE_TAG) and
            hasAnyAncestor(hasTestTag(MY_DRAWINGS_LIST_TAG))
        waitUntil {
            composeRule.onAllNodes(hasTestTag(HOME_SCREEN_TAG)).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodes(hasTestTag(MY_DRAWINGS_LIST_TAG))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertEquals(
            count,
            composeRule.onAllNodes(scores, useUnmergedTree = true).fetchSemanticsNodes().size
        )
    }

    private fun summaries(): List<CompletedDrawingSummary> = runBlocking {
        (fakeContainer.completedDrawingRepository.summaries() as StorageResult.Success).value
    }

    private fun List<GeoPoint>.toRoutePoints(): List<RoutePoint> {
        val start = System.currentTimeMillis() - size * 1_000L
        return mapIndexed { i, it -> RoutePoint(it.latitude, it.longitude, start + i * 1_000L) }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }

    private fun waitForText(text: String) {
        waitUntil { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
