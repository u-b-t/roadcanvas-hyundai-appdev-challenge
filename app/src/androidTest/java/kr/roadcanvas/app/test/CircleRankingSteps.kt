package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.circleranking.CIRCLE_RANKING_ROW_TAG
import kr.roadcanvas.app.circleranking.CIRCLE_RANKING_TITLE
import kr.roadcanvas.app.circleranking.CircleRankingEntry
import kr.roadcanvas.app.circleranking.MY_CIRCLE_OUTSIDE_ROW_TAG
import kr.roadcanvas.app.circleranking.MY_CIRCLE_RANKING_ROW_TAG
import kr.roadcanvas.app.circleranking.MY_CIRCLE_RECORD_TAG
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.modeselect.CIRCLE_RANKING_HOME_LABEL
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/** 동그라미 챌린지 순위 시나리오. 대체 순위 저장소에 기록을 두고 모드 선택에서 화면을 연다. */
class CircleRankingSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val store get() = fakeContainer.circleRankingStore
    private val myUid get() = FakeAppContainer.DEFAULT_VOTER_UID

    /** 순위표 안의 줄. 10위 밖이라 따로 보여주는 내 줄은 넣지 않는다. */
    private val topRows =
        hasTestTag(CIRCLE_RANKING_ROW_TAG) or hasTestTag(MY_CIRCLE_RANKING_ROW_TAG)

    @Given("동그라미 챌린지 순위에 기록이 있다")
    fun putEntries(table: DataTable) {
        table.asMaps().forEach { row ->
            store.put(
                CircleRankingEntry(
                    uid = row.getValue("UID"),
                    nickname = row.getValue("닉네임"),
                    score = row.getValue("점수").toInt(),
                    achievedAtMillis = noonOf(row.getValue("기록 날짜"))
                )
            )
        }
    }

    @Given("동그라미 챌린지 순위에 다른 사용자 {int}명이 {int}점부터 1점씩 낮은 점수로 있다")
    fun putOthers(count: Int, highest: Int) {
        (1..count).forEach {
            val number = it.toString().padStart(2, '0')
            store.put(
                CircleRankingEntry(
                    "u$number",
                    "드라이버-$number",
                    highest - it + 1,
                    noonOf("2026-11-01")
                )
            )
        }
    }

    @Given("동그라미 챌린지 순위에 내 기록 {int}점이 있다")
    fun putMine(score: Int) {
        store.put(CircleRankingEntry(myUid, "드라이버-test", score, noonOf("2026-11-02")))
    }

    @Given("{string} 사용자 {string}의 동그라미 순위 기록 {int}점이 반지름 50m 원 경로로 있다")
    fun putRecord(uid: String, nickname: String, score: Int) {
        store.putRecord(
            CircleRankingEntry(uid, nickname, score, noonOf("2026-11-01")),
            circlePath()
        )
    }

    @Given("{string} 사용자 {string}의 동그라미 순위 기록 {int}점이 경로 청크 하나가 빠진 채로 있다")
    fun putRecordMissingChunk(uid: String, nickname: String, score: Int) {
        store.putRecord(
            CircleRankingEntry(uid, nickname, score, noonOf("2026-11-01")),
            circlePath(),
            missingChunk = 1
        )
    }

    @Given("동그라미 기록 경로를 읽을 수 없다")
    fun failChunks() {
        store.chunkFailure = true
    }

    @Given("동그라미 기록 경로 읽기를 되돌린다")
    fun restoreChunks() {
        store.chunkFailure = false
    }

    @Given("동그라미 순위에서 {string} 줄을 누른다")
    fun openRecord(nickname: String) {
        val row = (topRows or hasTestTag(MY_CIRCLE_OUTSIDE_ROW_TAG)) and hasText(nickname)
        showInScroll(row)
        composeRule.onAllNodes(row).onFirst().performClick()
        composeRule.waitForIdle()
    }

    @Given("동그라미 순위 서버에 연결할 수 없다")
    fun disconnect() {
        store.networkFailure = true
    }

    @Given("동그라미 순위 서버 연결을 되돌린다")
    fun reconnect() {
        store.networkFailure = false
    }

    @Given("동그라미 순위 집계가 실패한다")
    fun failCount() {
        store.countFailure = true
    }

    @Given("메인 화면에서 동그라미 챌린지 순위 화면을 연다")
    fun openRanking() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        composeRule.clickHomeButton(CIRCLE_RANKING_HOME_LABEL)
        waitFor(hasText(CIRCLE_RANKING_TITLE))
        composeRule.waitForIdle()
    }

    @Then("동그라미 순위에 {int}명이 표시된다")
    fun assertRowCount(count: Int) {
        waitFor(topRows)
        assertEquals(count, composeRule.onAllNodes(topRows).fetchSemanticsNodes().size)
    }

    @Then("동그라미 순위 1위는 {string}, {string}, {string}이다")
    fun assertFirstRow(nickname: String, score: String, date: String) {
        assertRow(0, 1, nickname)
        composeRule.onAllNodes(topRows)[0].assert(hasText(score)).assert(hasText(date))
    }

    @Then("^동그라미 순위 (두|세) 번째 줄은 (\\d+)위 \"(.*)\"이다$")
    fun assertNthRow(order: String, rank: Int, nickname: String) {
        assertRow(if (order == "두") 1 else 2, rank, nickname)
    }

    @Then("동그라미 순위에서 내 순위 {int}위가 강조되어 표시된다")
    fun assertMyRow(rank: Int) {
        showInScroll(hasTestTag(MY_CIRCLE_RANKING_ROW_TAG) and hasText("$rank"))
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag(MY_CIRCLE_RANKING_ROW_TAG)).fetchSemanticsNodes().size
        )
    }

    @Then("순위표 아래에 내 순위 {int}위와 {string}이 따로 표시된다")
    fun assertOutsideRow(rank: Int, score: String) {
        showInScroll(hasTestTag(MY_CIRCLE_OUTSIDE_ROW_TAG) and hasText("$rank") and hasText(score))
        assertTrue(
            "순위표 안에 내 줄이 있다",
            composeRule.onAllNodes(hasTestTag(MY_CIRCLE_RANKING_ROW_TAG)).fetchSemanticsNodes()
                .isEmpty()
        )
    }

    @Then("내 순위 {string}가 화면 왼쪽 절반에 표시된다")
    @Then("내 최고 점수 {string}이 화면 왼쪽 절반에 표시된다")
    fun assertMyRecordOnLeft(text: String) {
        val matcher = hasText(text) and hasAnyAncestor(hasTestTag(MY_CIRCLE_RECORD_TAG))
        waitFor(matcher)
        val bounds = composeRule.onNode(matcher).getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("\"$text\" $bounds, 화면 $root", bounds.right <= (root.left + root.right) / 2)
    }

    private fun assertRow(index: Int, rank: Int, nickname: String) {
        waitFor(topRows)
        composeRule.onAllNodes(topRows)[index].assert(hasText("$rank")).assert(hasText(nickname))
    }

    /** 순위표가 스크롤되어 있어도 [matcher] 노드까지 스크롤해 보이는지 확인한다. */
    private fun showInScroll(matcher: SemanticsMatcher) {
        waitFor(matcher)
        val node = composeRule.onAllNodes(matcher).onFirst()
        if (composeRule.onAllNodes(matcher and hasAnyAncestor(hasScrollAction()))
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            node.performScrollTo()
        }
        node.assertIsDisplayed()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 반지름 50m 원을 1초 간격으로 기록한 경로 */
    private fun circlePath() = TestPaths.circle(50.0).mapIndexed { i, it ->
        RoutePoint(it.latitude, it.longitude, noonOf("2026-11-01") + i * 1_000L)
    }

    private fun noonOf(date: String): Long = LocalDate.parse(date)
        .atTime(LocalTime.NOON)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
