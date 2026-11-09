package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.cos
import kotlin.math.hypot
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.formatArea
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.map.fake.FakeRouteMapComponent
import kr.roadcanvas.app.modeselect.MODE_SELECT_TITLE
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.Geohash
import kr.roadcanvas.app.territory.TERRITORY_CELL_PRECISION
import kr.roadcanvas.app.territory.TERRITORY_SYNC_STATUS_TAG
import kr.roadcanvas.app.territory.TerritoryCellsResult
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.formatCells
import kr.roadcanvas.app.territory.territoryAreaOf
import kr.roadcanvas.app.territory.territoryCellsOf
import kr.roadcanvas.app.territorymap.DEFAULT_TERRITORY_MAP_CENTER
import kr.roadcanvas.app.territorymap.MY_RANKING_ROW_TAG
import kr.roadcanvas.app.territorymap.MY_RANK_LABEL
import kr.roadcanvas.app.territorymap.RANKING_ROW_TAG
import kr.roadcanvas.app.territorymap.TERRITORY_MAP_LABEL
import kr.roadcanvas.app.territorymap.TERRITORY_MAP_TITLE
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val FAKE_TERRITORY_MAP = "대체 영토 지도"
private const val METERS_PER_DEGREE_LATITUDE = 111_195.0

/**
 * 서버 땅따먹기 시나리오. 대체 서버 저장소에 칸을 두고 결과·영토 지도·내 그림 화면을 실제 흐름으로 연다.
 * 대체 지도는 둘러보기 위치를 가운데 둔 범위를 알리므로, 칸은 서울시청에서 가까운 칸부터 채운다.
 * 서버 연결 끊기와 제출 없음 확인은 [TerritoryCaptureSyncSteps]의 단계를 함께 쓴다.
 */
class TerritoryMapSteps(
    private val ruleHolder: ComposeRuleHolder,
    private val progressSteps: ChallengeProgressSteps,
    private val mapOverlaySteps: MapOverlaySteps
) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val store get() = fakeContainer.territoryStore

    /** 서울시청에서 가까운 순서의 칸. 표마다 이어서 나눠 주어 소유자끼리 칸이 겹치지 않는다. */
    private val nearbyCells: Iterator<String> by lazy { cellsNear(DEFAULT_TERRITORY_MAP_CENTER) }

    @Given("다른 사용자 {string}가 반지름 {int}m 원 영역의 칸을 차지하고 있다")
    fun putClaimedCircle(nickname: String, radius: Int) {
        val claimed = TerritoryCalculator().calculate(TestPaths.circle(radius.toDouble()))
        val boundary = (claimed as TerritoryResult.Claimed).boundary
        val cells = (territoryCellsOf(boundary) as TerritoryCellsResult.Cells).cells
        store.putCells("other", nickname, cells)
    }

    @Given("서울시청 주변 서버 칸 소유자가 있다")
    fun putOwners(table: DataTable) {
        table.asMaps().forEach { row ->
            val cells = List(row.getValue("칸 수").toInt()) { nearbyCells.next() }
            store.putCells(row.getValue("UID"), row.getValue("닉네임"), cells)
        }
    }

    @Given("서울시청 주변에 다른 사용자 칸 {int}개가 있다")
    fun putManyCells(count: Int) {
        store.putCells("other", "드라이버-other", List(count) { nearbyCells.next() })
    }

    @When("땅따먹기 서버 연결을 되돌린다")
    fun reconnect() {
        store.networkFailure = false
    }

    @Given("땅따먹기 익명 인증에 실패한다")
    fun failAuth() {
        fakeContainer.territoryOwnerProvider.owner = null
    }

    @When("땅따먹기 익명 인증을 되돌린다")
    fun restoreAuth() {
        fakeContainer.territoryOwnerProvider.owner = FakeTerritoryOwnerProvider.DEFAULT_OWNER
    }

    /**
     * 칸이 400개를 넘는 영역. 한 변을 100등분한 점으로 돌아 경로 점 수를 줄이되,
     * 마지막 점 바로 앞 점이 출발점에서 닫힘 허용 거리 안에 있게 한다.
     */
    @Given("한 변 {int}m인 정사각형을 땅따먹기로 기록하고 종료했다")
    fun finishLargeSquare(side: Int) {
        val corners = listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, 0.0 to 0.0)
        val local = corners.zipWithNext().flatMap { (from, to) ->
            List(100) { i ->
                val t = i / 100.0
                (from.first + (to.first - from.first) * t) * side to
                    (from.second + (to.second - from.second) * t) * side
            }
        } + (0.0 to 0.0)
        val start = System.currentTimeMillis() - local.size * 1_000L
        val origin = DEFAULT_TERRITORY_MAP_CENTER
        val path = local.mapIndexed { i, (east, north) ->
            RoutePoint(
                origin.latitude + north / METERS_PER_DEGREE_LATITUDE,
                origin.longitude + east /
                    (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(origin.latitude))),
                start + i * 1_000L
            )
        }
        progressSteps.finishSavedChallenge(path, DrawingMode.TERRITORY)
    }

    @Given("영토 지도 화면을 연다")
    fun openTerritoryMap() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        waitFor(hasText(MODE_SELECT_TITLE))
        val button = hasText(TERRITORY_MAP_LABEL) and hasClickAction()
        waitFor(button)
        composeRule.onNode(button).performClick()
        waitFor(hasText(TERRITORY_MAP_TITLE))
        composeRule.waitForIdle()
    }

    @Then("서버 반영 상태는 {string}이다")
    @Then("서버 반영 상태는 {string}다")
    fun assertSyncStatus(label: String) {
        showInScroll(hasTestTag(TERRITORY_SYNC_STATUS_TAG) and hasText(label))
    }

    @Then("서버 반영 안내에 {string}가 표시된다")
    @Then("서버 반영 안내에 {string}이 표시된다")
    fun assertSyncText(text: String) {
        showInScroll(hasText(text))
    }

    @Then("서버 반영 안내에 {string} 버튼이 있다")
    fun assertSyncButton(label: String) {
        showInScroll(hasText(label) and hasClickAction())
    }

    @Then("서버에 반영한 새로 차지한 칸, 빼앗은 칸, 이미 내 칸 수와 환산 면적이 화면에 표시된다")
    fun assertOutcomeShown() {
        val outcome = requireNotNull(store.capture("capture-1")).outcome
        listOf(outcome.newCells, outcome.stolenCells, outcome.ownCells).forEach {
            showInScroll(hasText(formatCells(it.toLong())))
        }
        showInScroll(hasText(formatArea(territoryAreaOf(outcome.totalCells.toLong()))))
    }

    @Then("빼앗은 칸은 {int}칸 이상이다")
    fun assertStolen(min: Int) {
        val outcome = requireNotNull(store.capture("capture-1")).outcome
        assertTrue("빼앗은 칸 ${outcome.stolenCells}", outcome.stolenCells >= min)
    }

    @Then("서버에 제출한 획득 ID는 모두 {string}이다")
    fun assertSubmittedIds(captureId: String) {
        waitUntil { store.capture(captureId) != null }
        assertEquals(setOf(captureId), store.submitRequests.toSet())
    }

    @Then("지도에 내 칸 {int}개와 다른 사용자 칸 {int}개가 표시된다")
    fun assertMapCells(mine: Int, others: Int) {
        waitFor(hasText("$FAKE_TERRITORY_MAP · 내 칸 ${mine}개 · 다른 사용자 칸 ${others}개"))
    }

    @Then("지도에 내 칸만 표시되고 다른 사용자 칸은 없다")
    fun assertOnlyMyCells() {
        waitUntil { store.owner(FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid) != null }
        val mine = store.cells.count { it.value == FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid }
        assertTrue("내 칸 $mine", mine > 0)
        assertMapCells(mine, 0)
    }

    @Then("내 보유 칸 수가 서버에 반영한 칸 수와 같다")
    fun assertMyCellCount() {
        val count = requireNotNull(store.owner(FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid))
            .cellCount
        mapOverlaySteps.assertTextInControls(formatCells(count))
    }

    @Then("칸 {int}개의 환산 면적이 화면 왼쪽 조작 영역에 표시된다")
    fun assertArea(count: Int) {
        mapOverlaySteps.assertTextInControls(formatArea(territoryAreaOf(count.toLong())))
    }

    @Then("순위에 {int}명이 표시된다")
    fun assertRankingSize(count: Int) {
        val rows = hasTestTag(RANKING_ROW_TAG) or hasTestTag(MY_RANKING_ROW_TAG)
        waitFor(rows)
        assertEquals(count, composeRule.onAllNodes(rows).fetchSemanticsNodes().size)
    }

    @Then("순위에 {string}과 {string}이 표시된다")
    @Then("순위에 {string}와 {string}이 표시된다")
    fun assertRankingRow(nickname: String, cells: String) {
        val row = (hasTestTag(RANKING_ROW_TAG) or hasTestTag(MY_RANKING_ROW_TAG)) and
            hasText(nickname) and hasText(cells)
        showInScroll(row)
    }

    @Then("내 순위 {int}위가 강조되어 표시된다")
    fun assertMyRank(rank: Int) {
        showInScroll(hasTestTag(MY_RANKING_ROW_TAG) and hasText("$rank") and hasText(MY_RANK_LABEL))
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag(MY_RANKING_ROW_TAG)).fetchSemanticsNodes().size
        )
    }

    /** 오버레이 카드가 스크롤되어 있어도 [matcher] 노드까지 스크롤해 보이는지 확인한다. */
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
        waitUntil { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }

    private companion object {
        /**
         * [center]에서 가까운 순서의 geohash7 칸. 대체 지도가 알리는 범위 안에 들도록
         * 범위 폭의 절반보다 조금 안쪽까지만 고른다.
         */
        fun cellsNear(center: kr.roadcanvas.app.map.RoutePoint): Iterator<String> {
            val reach = FakeRouteMapComponent.VISIBLE_SPAN_DEGREES / 2 * 0.9
            val step = Geohash.latitudeSpan(TERRITORY_CELL_PRECISION) / 2
            val steps = (reach / step).toInt()
            return (-steps..steps).flatMap { row -> (-steps..steps).map { col -> row to col } }
                .sortedBy { (row, col) -> hypot(row.toDouble(), col.toDouble()) }
                .map { (row, col) ->
                    Geohash.encode(
                        GeoPoint(center.latitude + row * step, center.longitude + col * step),
                        TERRITORY_CELL_PRECISION
                    )
                }
                .distinct()
                .iterator()
        }
    }
}
