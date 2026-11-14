package kr.roadcanvas.app.test

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.challenge.formatArea
import kr.roadcanvas.app.challenge.territoryBoundaryOf
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.TERRITORY_AREA_METRIC
import kr.roadcanvas.app.map.MapCell
import kr.roadcanvas.app.map.MapCellStyle
import kr.roadcanvas.app.map.RoutePoint as MapPoint
import kr.roadcanvas.app.map.fake.FakeRouteMapComponent
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryCalculator
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.territory.TERRITORY_CELL_LAT_STEP
import kr.roadcanvas.app.territory.TERRITORY_CELL_LON_STEP
import kr.roadcanvas.app.territory.TERRITORY_SYNC_STATUS_TAG
import kr.roadcanvas.app.territory.TerritoryCellsResult
import kr.roadcanvas.app.territory.TerritoryGrid
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
import kr.roadcanvas.app.vehicle.LocationSample
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val FAKE_TERRITORY_MAP = "대체 영토 지도"
private const val METERS_PER_DEGREE_LATITUDE = 111_195.0
private const val FAKE_CURRENT_POSITION = "대체 현재 위치"

/** 지도 중심 비교 허용 거리. 기록 경로 가운데와 원 중심의 차이를 받아들인다. */
private const val CENTER_TOLERANCE_METERS = 20.0

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

    @Given("다른 사용자 {string}가 반지름 {int}m 원 영역의 칸 {int}개와 그 바깥 칸 {int}개를 차지하고 있다")
    fun putClaimedAndOutsideCells(nickname: String, radius: Int, inside: Int, outside: Int) {
        val boundary = claimedBoundary(radius)
        store.putCells(
            "other",
            nickname,
            centralCells(boundary).take(inside) + outsideCells(boundary).take(outside)
        )
    }

    @Given("내가 반지름 {int}m 원 영역의 칸 {int}개를 이미 차지하고 있다")
    fun putMyClaimedCells(radius: Int, count: Int) {
        // 다른 사용자에게 준 가운데 칸과 겹치지 않도록 그 다음 칸부터 준다.
        val cells = centralCells(claimedBoundary(radius)).drop(MY_CELLS_OFFSET).take(count)
        val owner = FakeTerritoryOwnerProvider.DEFAULT_OWNER
        store.putCells(owner.uid, owner.nickname, cells)
    }

    @Then("결과 지도에 빼앗은 칸 {int}개, 이미 내 칸 {int}개, 나머지 차지한 칸과 주변 다른 사용자 칸 {int}개가 표시된다")
    fun assertResultMapCells(stolen: Int, kept: Int, others: Int) {
        waitUntil { savedDrawings().isNotEmpty() }
        val boundary = requireNotNull(territoryBoundaryOf(savedDrawings().single()))
        val total = (territoryCellsOf(boundary) as TerritoryCellsResult.Cells).cells.size
        assertTrue("차지한 칸 $total", total > stolen + kept)
        val expected = FakeRouteMapComponent.describeResultCells(
            List(total - stolen - kept) { resultCell(MapCellStyle.CAPTURED) } +
                List(stolen) { resultCell(MapCellStyle.STOLEN) } +
                List(kept) { resultCell(MapCellStyle.KEPT) } +
                List(others) { resultCell(MapCellStyle.FAINT, mine = false) }
        ).substringBefore(" · 칸 글자")
        waitFor(hasText(expected, substring = true))
    }

    @Then("결과 지도의 빼앗은 칸에 {string}가 표시된다")
    @Then("결과 지도의 빼앗은 칸에 {string}이 표시된다")
    fun assertResultMapLabel(nickname: String) {
        waitFor(hasText("칸 글자 $nickname", substring = true))
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
        composeRule.clickHomeButton(TERRITORY_MAP_LABEL)
        waitFor(hasText(TERRITORY_MAP_TITLE))
        composeRule.waitForIdle()
    }

    @Given("서울시청에서 북쪽으로 {int}m 떨어진 곳에 차지한 땅따먹기 기록이 있다")
    fun saveClaimedTerritoryNorth(meters: Int) {
        // 북쪽 끝에서 출발해 반지름 50m 원을 돌아 출발점으로 돌아온다.
        val center = northOfCityHall(meters.toDouble())
        val start = System.currentTimeMillis() - 60_000L
        val path = (0..36).map { i ->
            val angle = Math.toRadians(90.0 + i * 10.0)
            RoutePoint(
                center.latitude + 50.0 * sin(angle) / METERS_PER_DEGREE_LATITUDE,
                center.longitude + 50.0 * cos(angle) /
                    (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(center.latitude))),
                start + i * 1_000L
            )
        }
        val drawing = CompletedDrawing(
            mode = DrawingMode.TERRITORY,
            completedAtMillis = start + 60_000L,
            path = path,
            score = 0,
            metrics = mapOf(TERRITORY_AREA_METRIC to 7_854.0),
            distanceMeters = 314.0,
            durationMillis = 36_000L
        )
        val saved = runBlocking { fakeContainer.completedDrawingRepository.save(drawing) }
        assertTrue("저장 결과: $saved", saved is StorageResult.Success)
    }

    @When("현재 위치를 서울시청에서 북쪽으로 {int}m 떨어진 곳으로 받는다")
    fun sendLocationNorth(meters: Int) {
        val point = northOfCityHall(meters.toDouble())
        val location = fakeContainer.locationProvider
        runBlocking {
            withTimeout(WAIT_TIMEOUT_MILLIS) { location.subscriberCount.first { it > 0 } }
            location.sendLocation(
                LocationSample(point.latitude, point.longitude, System.currentTimeMillis())
            )
        }
    }

    @Then("영토 지도 중심은 서울시청에서 북쪽으로 {int}m 떨어진 곳이다")
    fun assertMapCenterNorth(meters: Int) {
        val expected = northOfCityHall(meters.toDouble())
        var last: MapPoint? = null
        val reached = runCatching {
            waitUntil {
                last = currentMapCenter()
                last?.let { distanceMeters(it, expected) <= CENTER_TOLERANCE_METERS } == true
            }
        }
        assertTrue("지도 중심 $last, 기대 $expected", reached.isSuccess)
    }

    @Then("지도에 내 위치가 표시된다")
    fun assertMyLocationShown() {
        waitFor(hasText(FAKE_CURRENT_POSITION, substring = true))
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

    private fun claimedBoundary(radius: Int): List<GeoPoint> = (
        TerritoryCalculator().calculate(
            TestPaths.circle(radius.toDouble())
        ) as TerritoryResult.Claimed
        )
        .boundary

    /** 영역 가운데에서 가까운 순서의 칸. 기록 경로가 조금 달라도 영역 안에 남는 칸부터 고른다. */
    private fun centralCells(boundary: List<GeoPoint>): List<String> {
        val center = MapPoint(
            boundary.map { it.latitude }.average(),
            boundary.map { it.longitude }.average()
        )
        return (territoryCellsOf(boundary) as TerritoryCellsResult.Cells).cells.sortedBy {
            val cell = TerritoryGrid.bounds(it).center
            distanceMeters(center, MapPoint(cell.latitude, cell.longitude))
        }
    }

    /** 영역 북쪽 바로 바깥의 칸. 칸 가운데가 영역 북쪽 끝보다 칸 높이의 1/4 이상 북쪽에 있다. */
    private fun outsideCells(boundary: List<GeoPoint>): List<String> {
        val latitude = boundary.maxOf { it.latitude } +
            TERRITORY_CELL_LAT_STEP * 0.75
        val west = boundary.minOf { it.longitude }
        val east = boundary.maxOf { it.longitude }
        val step = TERRITORY_CELL_LON_STEP
        return generateSequence(west) { it + step }.takeWhile { it <= east }
            .map { TerritoryGrid.cellOf(GeoPoint(latitude, it)).id }
            .distinct()
            .toList()
    }

    private fun resultCell(style: MapCellStyle, mine: Boolean = true) =
        MapCell(emptyList(), mine, style)

    private fun savedDrawings(): List<CompletedDrawing> {
        val repository = fakeContainer.completedDrawingRepository
        val summaries = (runBlocking { repository.summaries() } as StorageResult.Success).value
        return summaries.mapNotNull {
            (runBlocking { repository.find(it.id) } as StorageResult.Success).value
        }
    }

    /** 대체 지도가 보여주는 둘러보기 중심. 아직 정하지 않았으면 null이다. */
    private fun currentMapCenter(): MapPoint? {
        val prefix = FakeRouteMapComponent.describeCenter(DEFAULT_TERRITORY_MAP_CENTER)
            .substringBefore('·')
        val text = composeRule.onAllNodes(hasText(prefix, substring = true))
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            } ?: return null
        val (latitude, longitude) = text.substringAfter('·').split(',').map { it.trim().toDouble() }
        return MapPoint(latitude, longitude)
    }

    private fun northOfCityHall(meters: Double) = MapPoint(
        DEFAULT_TERRITORY_MAP_CENTER.latitude + meters / METERS_PER_DEGREE_LATITUDE,
        DEFAULT_TERRITORY_MAP_CENTER.longitude
    )

    private fun distanceMeters(a: MapPoint, b: MapPoint): Double = hypot(
        (a.latitude - b.latitude) * METERS_PER_DEGREE_LATITUDE,
        (a.longitude - b.longitude) * METERS_PER_DEGREE_LATITUDE *
            cos(Math.toRadians(a.latitude))
    )

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
        /** 내 칸으로 줄 칸을 영역 가운데에서 몇 번째 칸부터 고를지. 다른 사용자 칸 2개 다음이다. */
        const val MY_CELLS_OFFSET = 2

        /**
         * [center]에서 가까운 순서의 격자 칸. 대체 지도가 알리는 범위 안에 들도록
         * 범위 폭의 절반보다 조금 안쪽까지만 고른다.
         */
        fun cellsNear(center: kr.roadcanvas.app.map.RoutePoint): Iterator<String> {
            val reach = FakeRouteMapComponent.VISIBLE_SPAN_DEGREES / 2 * 0.9
            val step = TERRITORY_CELL_LAT_STEP / 2
            val steps = (reach / step).toInt()
            return (-steps..steps).flatMap { row -> (-steps..steps).map { col -> row to col } }
                .sortedBy { (row, col) -> hypot(row.toDouble(), col.toDouble()) }
                .map { (row, col) ->
                    TerritoryGrid.cellOf(
                        GeoPoint(
                            center.latitude + row * step,
                            center.longitude + col * step
                        )
                    ).id
                }
                .distinct()
                .iterator()
        }
    }
}
