package kr.roadcanvas.app.test

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.CIRCLE_PROGRESS_TITLE
import kr.roadcanvas.app.challenge.CIRCLE_READY_TITLE
import kr.roadcanvas.app.challenge.CIRCLE_RESULT_TITLE
import kr.roadcanvas.app.challenge.DISTANCE_TO_START_LABEL
import kr.roadcanvas.app.challenge.FINISH_LABEL
import kr.roadcanvas.app.challenge.FREE_DRAWING_RESULT_TITLE
import kr.roadcanvas.app.challenge.START_LABEL
import kr.roadcanvas.app.challenge.TERRITORY_RESULT_TITLE
import kr.roadcanvas.app.challenge.TURN_PROGRESS_LABEL
import kr.roadcanvas.app.challenge.progressTitleOf
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.modeselect.CIRCLE_CHALLENGE_NAME
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.recording.RecordingPermissions
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val DAY_MILLIS = 86_400_000L
private const val CIRCLE_SPACING_METERS = 5.0
private const val BASE_LATITUDE = 37.5665
private const val BASE_LONGITUDE = 126.978
private const val EARTH_RADIUS_METERS = 6_371_000.0

/** 진행 화면 시나리오. 기록은 포그라운드 서비스 대신 테스트용 실행기가 코루틴에서 실행한다. */
class ChallengeProgressSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val application = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
    private val fakeContainer get() = application.container as FakeAppContainer
    private val recorder get() = fakeContainer.challengeRecorder
    private var scenario: TestScreen<ComponentActivity>? = null
    private var rememberedHeight: Float? = null

    /** 시작 버튼이 권한 요청 창을 띄우지 않도록 기록에 필요한 권한을 미리 허용한다. */
    @Before
    fun grantRecordingPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        RecordingPermissions.runtime().forEach {
            instrumentation.uiAutomation.grantRuntimePermission(application.packageName, it)
        }
    }

    @Given("동그라미 챌린지 진행 화면에서 시작을 누른다")
    fun pressStart() {
        openApp()
        waitForText(MODE_SELECT_TITLE)
        composeRule.onNode(hasText(CIRCLE_CHALLENGE_NAME) and hasClickAction()).performClick()
        waitForText(CIRCLE_READY_TITLE)
        waitUntil { fakeContainer.locationProvider.subscriberCount.value > 0 }
        runBlocking { fakeContainer.locationProvider.sendLocation(LocationSample(0.0, 0.0, 0L)) }
        composeRule.onNode(hasText(START_LABEL) and hasClickAction()).performClick()
        waitForText(CIRCLE_PROGRESS_TITLE)
    }

    @Given("동그라미 챌린지 진행 화면에서 기록을 시작했다")
    fun startRecording() {
        pressStart()
        // 준비 화면의 위치 구독이 해제되고 기록 세션만 위치를 받을 때까지 기다린다.
        waitUntil {
            recorder.state.value is RecordingState.Recording &&
                fakeContainer.locationProvider.subscriberCount.value == 1 &&
                composeRule.onAllNodesWithText(CIRCLE_READY_TITLE).fetchSemanticsNodes().isEmpty()
        }
    }

    @Given("경로 점 {int}개를 기록한 진행 중인 도전이 저장되어 있다")
    fun saveInProgress(count: Int) {
        saveInProgress(count, DrawingMode.CIRCLE)
    }

    fun saveInProgress(count: Int, mode: DrawingMode) {
        val repository = fakeContainer.inProgressDrawingRepository
        runBlocking {
            repository.start(mode, System.currentTimeMillis())
            repository.appendPoints(
                (0 until count).map { RoutePoint(37.5665 + 0.0009 * it, 126.978, it.toLong()) }
            )
        }
    }

    /** [path]를 기록한 [mode] 도전을 저장해 두고, 앱을 다시 실행해 이어간 뒤 종료해 결과 화면을 연다. */
    fun finishSavedChallenge(path: List<RoutePoint>, mode: DrawingMode = DrawingMode.CIRCLE) {
        runBlocking {
            val repository = fakeContainer.inProgressDrawingRepository
            repository.start(mode, System.currentTimeMillis() - path.size * 1_000L)
            repository.appendPoints(path)
        }
        openApp()
        waitForText(progressTitleOf(mode))
        waitForResume()
        val finish = hasText(FINISH_LABEL) and hasClickAction() and isEnabled()
        waitUntil { composeRule.onAllNodes(finish).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(finish).performClick()
        waitForText(resultTitles.getValue(mode))
    }

    private val resultTitles = mapOf(
        DrawingMode.CIRCLE to CIRCLE_RESULT_TITLE,
        DrawingMode.FREE_DRAWING to FREE_DRAWING_RESULT_TITLE,
        DrawingMode.TERRITORY to TERRITORY_RESULT_TITLE
    )

    @Given("진행 중 기록 저장이 실패하도록 설정되어 있다")
    fun failInProgressStorage() {
        fakeContainer.inProgressDrawingRepository.failure = IllegalStateException("테스트 저장소 오류")
    }

    @When("앱을 다시 실행한다")
    fun relaunch() {
        // 열려 있던 화면을 닫고 새 화면을 연다. 화면 상태는 버리고 저장소와 기록 세션만 이어진다.
        TestScreens.closeAll()
        openApp()
    }

    @When("앱 프로세스가 종료된 뒤 앱을 다시 실행한다")
    fun relaunchAfterProcessDeath() {
        // 화면과 기록 세션을 모두 버리고 저장소만 남긴 채 새 화면을 연다.
        TestScreens.closeAll()
        fakeContainer.restartProcess()
        // 이전 기록 세션이 위치를 더 받아 저장하지 않도록 구독이 끝날 때까지 기다린다.
        waitUntil { fakeContainer.locationProvider.subscriberCount.value == 0 }
        openApp()
    }

    @Then("기록을 이어서 시작하지 않았다")
    fun assertNotResumed() {
        composeRule.waitForIdle()
        check(fakeContainer.recordingRunner.requests.value.isEmpty()) {
            "기록 실행 요청: ${fakeContainer.recordingRunner.requests.value}"
        }
        check(fakeContainer.locationProvider.subscriberCount.value == 0) { "위치를 구독하고 있습니다." }
    }

    @When("결과 화면에서 뒤로 가기를 누른다")
    fun pressBack() {
        composeRule.waitForIdle()
        requireNotNull(scenario).onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @When("이어서 기록이 시작될 때까지 기다린다")
    fun waitForResume() {
        waitUntil {
            recorder.state.value is RecordingState.Recording &&
                fakeContainer.locationProvider.subscriberCount.value > 0
        }
    }

    @When("^테스트에서 위치 시각이 \"(시작 하루 전|시작 1년 뒤|없음)\"인 위도 ([-\\d.]+), 경도 ([-\\d.]+) 위치를 기록한다$")
    fun recordLocationWithSampleTime(sampleTime: String, latitude: String, longitude: String) {
        val startedAtMillis = (recorder.state.value as RecordingState.Recording).startedAtMillis
        val timeMillis = when (sampleTime) {
            "시작 하루 전" -> startedAtMillis - DAY_MILLIS
            "시작 1년 뒤" -> startedAtMillis + 365 * DAY_MILLIS
            else -> 0L
        }
        recordLocation(latitude, longitude, timeMillis)
    }

    @When("^테스트에서 위도 ([-\\d.]+), 경도 ([-\\d.]+) 위치를 기록한다$")
    fun recordLocation(latitude: String, longitude: String) {
        recordLocation(latitude, longitude, System.currentTimeMillis())
    }

    private fun recordLocation(latitude: String, longitude: String, timeMillis: Long) {
        val point = RoutePoint(latitude.toDouble(), longitude.toDouble(), 0L)
        runBlocking {
            fakeContainer.locationProvider.sendLocation(
                LocationSample(point.latitude, point.longitude, timeMillis)
            )
        }
        // 위치는 보낸 순서대로 처리되므로 마지막 점이 이 좌표가 되면 앞의 위치도 모두 처리된 것이다.
        waitUntil {
            val last = (recorder.state.value as? RecordingState.Recording)?.path?.lastOrNull()
            last?.latitude == point.latitude && last.longitude == point.longitude
        }
    }

    @Then("경로 점의 시각은 모두 도전 시작 시각 이후다")
    fun assertPointTimesAfterStart() {
        val state = recorder.state.value as RecordingState.Recording
        assertTrue(state.path.all { it.timeMillis >= state.startedAtMillis })
    }

    /** 서울시청에서 출발해 동쪽으로 향하며 반시계 방향으로 도는 원을 5m 간격으로 기록한다. */
    @When("테스트에서 반지름 {int}m 원을 {double}바퀴 기록한다")
    fun recordCircle(radius: Int, turns: Double) {
        val count = (2 * PI * radius * turns / CIRCLE_SPACING_METERS).toInt()
        val points = (0..count).map { i ->
            val angle = 2 * PI * turns * i / count
            offset(east = radius * sin(angle), north = radius - radius * cos(angle))
        }
        runBlocking {
            points.forEach {
                fakeContainer.locationProvider.sendLocation(
                    LocationSample(it.latitude, it.longitude, System.currentTimeMillis())
                )
            }
        }
        waitUntil { (recorder.state.value as? RecordingState.Recording)?.path?.size == points.size }
    }

    @Then("출발점까지 거리는 {string}다")
    fun assertDistanceToStart(value: String) {
        waitUntil {
            composeRule.onAllNodes(hasText(DISTANCE_TO_START_LABEL) and hasText(value))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Then("회전 진행률은 {int}%에서 {int}% 사이다")
    fun assertTurnPercent(min: Int, max: Int) {
        waitUntil {
            val percent = measurementValue(TURN_PROGRESS_LABEL)?.removeSuffix("%")?.toIntOrNull()
            percent != null && percent in min..max
        }
    }

    @Given("{string} 측정값의 글자 크기를 기억한다")
    fun rememberMeasurementHeight(label: String) {
        waitUntil { measurementNode(label) != null }
        rememberedHeight = requireNotNull(measurementNode(label)).boundsInRoot.height
    }

    @Then("{string} 측정값이 기억한 크기보다 크게 표시된다")
    fun assertMeasurementLarger(label: String) {
        val before = requireNotNull(rememberedHeight) { "기억한 크기가 없습니다." }
        waitUntil { (measurementNode(label)?.boundsInRoot?.height ?: 0f) > before }
    }

    @Then("기록한 경로 점은 {int}개다")
    fun assertRecordedCount(count: Int) {
        waitUntil { (recorder.state.value as? RecordingState.Recording)?.path?.size == count }
    }

    @Then("진행 중 기록에 경로 점 {int}개가 저장되어 있다")
    fun assertSavedCount(count: Int) {
        waitUntil { savedInProgress()?.path?.size == count }
    }

    @Then("진행 중 기록이 없다")
    fun assertNoInProgress() {
        waitUntil { savedInProgress() == null }
    }

    @Then("완료 기록이 없다")
    fun assertNoCompleted() {
        val summaries = runBlocking { fakeContainer.completedDrawingRepository.summaries() }
        check(summaries == StorageResult.Success(emptyList<Any>())) { "완료 기록: $summaries" }
    }

    @Then("위치 구독이 해제된다")
    fun assertLocationUnsubscribed() {
        waitUntil { fakeContainer.locationProvider.subscriberCount.value == 0 }
    }

    fun openApp() {
        composeRule.mainClock.autoAdvance = true
        scenario = TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
    }

    /** 이름과 값을 묶은 측정값 노드 */
    private fun measurementNode(label: String) =
        composeRule.onAllNodesWithText(label).fetchSemanticsNodes().firstOrNull()

    private fun measurementValue(label: String): String? = measurementNode(label)
        ?.config
        ?.getOrNull(SemanticsProperties.Text)
        ?.map { it.text }
        ?.firstOrNull { it != label }

    /** 서울시청에서 동쪽 [east] m, 북쪽 [north] m 떨어진 점 */
    private fun offset(east: Double, north: Double): RoutePoint {
        val latitude = BASE_LATITUDE + Math.toDegrees(north / EARTH_RADIUS_METERS)
        val longitude = BASE_LONGITUDE +
            Math.toDegrees(east / (EARTH_RADIUS_METERS * cos(Math.toRadians(BASE_LATITUDE))))
        return RoutePoint(latitude, longitude, 0L)
    }

    private fun savedInProgress() = runBlocking {
        (fakeContainer.inProgressDrawingRepository.load() as? StorageResult.Success)?.value
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }

    private fun waitForText(text: String) {
        waitUntil { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
