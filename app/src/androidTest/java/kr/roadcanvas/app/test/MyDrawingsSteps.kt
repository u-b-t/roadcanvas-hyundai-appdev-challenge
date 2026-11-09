package kr.roadcanvas.app.test

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL
import kr.roadcanvas.app.mydrawings.DRAWING_DELETE_BUTTON_TAG
import kr.roadcanvas.app.mydrawings.DRAWING_DETAIL_OVERLAY_TAG
import kr.roadcanvas.app.mydrawings.DRAWING_DETAIL_TAG
import kr.roadcanvas.app.mydrawings.MODE_THUMBNAIL_DESCRIPTION
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_ITEM_SCORE_TAG
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_LIST_TAG
import kr.roadcanvas.app.mydrawings.MY_DRAWINGS_TITLE
import kr.roadcanvas.app.mydrawings.ROUTE_THUMBNAIL_DESCRIPTION
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val FAKE_MAP_PREFIX = "대체 경로 지도"
private val TABLE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 내 그림 시나리오. 대체 완료 기록 저장소에 기록을 넣고 실제 화면 흐름으로 들어간다. */
class MyDrawingsSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer

    @Given("저장된 동그라미 기록이 있다")
    fun saveDrawings(table: DataTable) {
        table.asMaps().forEach { row ->
            val completedAt = LocalDateTime.parse(row.getValue("완료 일시"), TABLE_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val pointCount = row.getValue("경로 점").toInt()
            val drawing = CompletedDrawing(
                mode = DrawingMode.CIRCLE,
                completedAtMillis = completedAt,
                path = List(pointCount) { index ->
                    RoutePoint(37.5665 + index * 0.0001, 126.978, completedAt + index * 1_000L)
                },
                score = row.getValue("점수").toInt(),
                metrics = mapOf("circularity" to 0.9),
                distanceMeters = row.getValue("거리(m)").toDouble(),
                durationMillis = row.getValue("소요 시간(초)").toLong() * 1_000
            )
            val result = runBlocking { fakeContainer.completedDrawingRepository.save(drawing) }
            assertTrue("저장 결과: $result", result is StorageResult.Success)
        }
    }

    @Given("내 그림 화면을 연다")
    fun openMyDrawings() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        // 실제 앱과 같은 흐름으로 모드 선택에서 들어간다.
        waitFor(hasText(MY_DRAWINGS_LABEL) and hasClickAction())
        composeRule.onNode(hasText(MY_DRAWINGS_LABEL) and hasClickAction()).performClick()
        waitFor(hasText(MY_DRAWINGS_TITLE))
        composeRule.waitForIdle()
    }

    @When("테스트용 저장소의 실패를 해제한다")
    fun clearFailure() {
        fakeContainer.completedDrawingRepository.failure = null
        fakeContainer.inProgressDrawingRepository.failure = null
    }

    @When("목록에서 {string} 기록을 누른다")
    fun clickListItem(text: String) {
        val item = hasText(text) and hasClickAction() and inList
        waitFor(item)
        composeRule.onNode(item).performClick()
    }

    @When("확인 대화상자에서 {string}를 누른다")
    @When("확인 대화상자에서 {string}을 누른다")
    fun clickDialogButton(label: String) {
        val button = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())
        waitFor(button)
        composeRule.onNode(button).performClick()
    }

    @Then("상세에 {string}가 표시된다")
    @Then("상세에 {string}이 표시된다")
    fun assertDetailShows(text: String) {
        val node = hasText(text) and hasAnyAncestor(hasTestTag(DRAWING_DETAIL_TAG))
        waitFor(node, useUnmergedTree = true)
        composeRule.onNode(node, useUnmergedTree = true).assertIsDisplayed()
    }

    @Then("목록의 점수는 {string} 순서다")
    fun assertListOrder(scores: String) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) { listScores() == scores }
        assertEquals(scores, listScores())
    }

    @Then("목록이 화면 왼쪽, 상세가 화면 오른쪽에 표시된다")
    fun assertListLeftOfDetail() {
        waitFor(hasTestTag(DRAWING_DETAIL_TAG))
        val list = composeRule.onNodeWithTag(MY_DRAWINGS_LIST_TAG).getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag(DRAWING_DETAIL_TAG).getUnclippedBoundsInRoot()
        assertTrue("목록이 상세 왼쪽에 있어야 한다", list.right <= detail.left)
    }

    @Then("목록의 {string} 항목에 {string}가 표시된다")
    @Then("목록의 {string} 항목에 {string}이 표시된다")
    fun assertListItemShows(date: String, text: String) {
        val item = hasText(date) and hasText(text) and hasClickAction() and inList
        waitFor(item)
        composeRule.onNode(item).assertIsDisplayed()
    }

    @Then("목록의 {string} 항목에 경로 썸네일이 표시된다")
    fun assertRouteThumbnail(date: String) {
        val item = hasText(date) and hasClickAction() and inList
        waitFor(item and hasContentDescription(ROUTE_THUMBNAIL_DESCRIPTION))
        composeRule.onNode(item).assert(!hasContentDescription(MODE_THUMBNAIL_DESCRIPTION))
    }

    @Then("목록의 {string} 항목에는 경로 썸네일 대신 모드 그림이 표시된다")
    fun assertModeThumbnail(date: String) {
        val item = hasText(date) and hasClickAction() and inList
        waitFor(item and hasContentDescription(MODE_THUMBNAIL_DESCRIPTION))
        composeRule.onNode(item).assert(!hasContentDescription(ROUTE_THUMBNAIL_DESCRIPTION))
    }

    @Then("{string} 버튼은 점수 카드 밖 상세 왼쪽 아래에 있다")
    fun assertDeleteButtonPlacement(label: String) {
        val node = hasTestTag(DRAWING_DELETE_BUTTON_TAG) and hasText(label) and hasClickAction()
        waitFor(node)
        val button = composeRule.onNode(node).getUnclippedBoundsInRoot()
        val overlay = composeRule.onNodeWithTag(
            DRAWING_DETAIL_OVERLAY_TAG
        ).getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag(DRAWING_DETAIL_TAG).getUnclippedBoundsInRoot()
        assertTrue("버튼 $button, 점수 카드 $overlay", button.top >= overlay.bottom)
        assertTrue("버튼 $button, 상세 $detail", button.left >= detail.left)
        assertTrue(
            "버튼 $button, 상세 $detail",
            button.right <= (detail.left + detail.right) / 2
        )
        assertTrue(
            "버튼 $button, 상세 $detail",
            button.top >= (detail.top + detail.bottom) / 2
        )
    }

    @Then("상세 지도 위 왼쪽 오버레이에 {string}이 표시된다")
    fun assertDetailOverlay(score: String) {
        val inOverlay = hasAnyAncestor(hasTestTag(DRAWING_DETAIL_OVERLAY_TAG))
        waitFor(hasText(score) and inOverlay, useUnmergedTree = true)
        val overlay = composeRule.onNodeWithTag(
            DRAWING_DETAIL_OVERLAY_TAG
        ).getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag(DRAWING_DETAIL_TAG).getUnclippedBoundsInRoot()
        val list = composeRule.onNodeWithTag(MY_DRAWINGS_LIST_TAG).getUnclippedBoundsInRoot()
        assertTrue("오버레이 $overlay, 목록 $list", overlay.left >= list.right)
        assertTrue(
            "오버레이 $overlay, 상세 $detail",
            overlay.right <= (detail.left + detail.right) / 2
        )
    }

    @Then("경로 지도는 오버레이 오른쪽 영역에 맞춰 그려진다")
    fun assertMapAvoidsOverlay() {
        // 대체 지도는 지도 콘텐츠 영역 가운데에 경로 설명을 표시한다.
        val map = hasText(FAKE_MAP_PREFIX, substring = true)
        waitFor(map)
        val overlay = composeRule.onNodeWithTag(
            DRAWING_DETAIL_OVERLAY_TAG
        ).getUnclippedBoundsInRoot()
        val notice = composeRule.onNode(map).getUnclippedBoundsInRoot()
        assertTrue("오버레이 $overlay, 지도 안내 $notice", notice.left >= overlay.right)
    }

    private val inList = hasAnyAncestor(hasTestTag(MY_DRAWINGS_LIST_TAG))

    private fun listScores(): String = composeRule
        .onAllNodes(hasTestTag(MY_DRAWINGS_ITEM_SCORE_TAG), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .joinToString {
            it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { text -> text.text }
                .orEmpty()
        }

    private fun waitFor(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
