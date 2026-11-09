package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.gallery.ARTWORK_DETAIL_OVERLAY_TAG
import kr.roadcanvas.app.gallery.GALLERY_ITEM_VOTES_TAG
import kr.roadcanvas.app.gallery.GALLERY_LIST_TAG
import kr.roadcanvas.app.gallery.GALLERY_OPEN_LABEL
import kr.roadcanvas.app.gallery.GALLERY_OVERLAY_TAG
import kr.roadcanvas.app.gallery.GALLERY_SELECTED_ITEM_TAG
import kr.roadcanvas.app.gallery.GALLERY_TITLE
import kr.roadcanvas.app.gallery.GALLERY_VOTES_LOADING
import kr.roadcanvas.app.gallery.fake.testServerArtwork
import kr.roadcanvas.app.modeselect.GALLERY_LABEL
import kr.roadcanvas.app.mydrawings.ROUTE_THUMBNAIL_DESCRIPTION
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L
private const val FAKE_MAP_PREFIX = "대체 경로 지도"

/** 공개 작품 시나리오. 대체 서버 저장소에 작품을 넣고 모드 선택에서 실제 화면 흐름으로 들어간다. */
class PublicGallerySteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val store get() = fakeContainer.publicArtworkStore

    @Given("서버에 공개 작품이 있다")
    fun putArtworks(table: DataTable) {
        table.asMaps().forEach { row ->
            store.put(
                testServerArtwork(
                    id = row.getValue("작품 ID"),
                    title = row.getValue("이름"),
                    nickname = row.getValue("닉네임"),
                    createdAtMillis = 1_700_000_000_000L + row.getValue("생성 순서").toLong() * 60_000,
                    pointCount = row.getValue("경로 점").toInt(),
                    voters = List(row.getValue("표 수").toInt()) { "voter-$it" }.toSet()
                )
            )
        }
    }

    @Given("서버에 공개 작품 {int}개가 있다")
    fun putManyArtworks(count: Int) {
        repeat(count) { index ->
            store.put(
                testServerArtwork(
                    id = "many-$index",
                    title = "작품 ${index + 1}",
                    createdAtMillis = 1_700_000_000_000L + index * 60_000L
                )
            )
        }
    }

    @Given("작성자가 {string} 작품을 비공개로 바꿨다")
    fun unpublish(artworkId: String) {
        store.setPublished(artworkId, published = false)
    }

    @Given("내가 {string} 작품에 이미 투표했다")
    fun voteAsMe(artworkId: String) {
        val artwork = requireNotNull(store.stored(artworkId))
        store.put(artwork.copy(voters = artwork.voters + requireNotNull(fakeContainer.voterUid)))
    }

    @Given("{string} 작품의 경로 청크 하나가 빠져 있다")
    fun dropChunk(artworkId: String) {
        val artwork = requireNotNull(store.stored(artworkId))
        store.put(artwork.copy(chunks = artwork.chunks.drop(1)))
    }

    @Given("공개 작품 서버에 연결할 수 없다")
    fun disconnect() {
        store.networkFailure = true
    }

    @When("공개 작품 서버 연결을 되돌린다")
    fun reconnect() {
        store.networkFailure = false
    }

    @Given("다음 투표는 서버에 저장되지만 응답을 받지 못한다")
    fun loseNextVoteResponse() {
        store.loseNextVoteResponse = true
    }

    @Given("공개 작품 화면을 연다")
    fun openGallery() {
        composeRule.mainClock.autoAdvance = true
        TestScreens.launchContent {
            RoadCanvasNavHost(navController = rememberNavController(), splashDurationMillis = 0L)
        }
        // 실제 앱과 같은 흐름으로 메인 화면에서 들어간다.
        composeRule.clickHomeButton(GALLERY_LABEL)
        waitFor(hasText(GALLERY_TITLE) and !hasClickAction())
        composeRule.waitForIdle()
    }

    @When("목록에서 {string} 작품을 고른다")
    fun selectArtwork(title: String) {
        val item = listItem(title)
        waitFor(item, useUnmergedTree = true)
        composeRule.onNode(item, useUnmergedTree = true).performClick()
    }

    /** 항목을 골라 그 항목의 작품 보기 버튼으로 상세에 들어간다. */
    @When("목록에서 {string} 작품을 연다")
    fun openArtwork(title: String) {
        selectArtwork(title)
        val open = hasText(GALLERY_OPEN_LABEL) and hasClickAction() and
            hasAnyAncestor(selectedItem(title))
        waitFor(open, useUnmergedTree = true)
        composeRule.onNode(open, useUnmergedTree = true).performClick()
    }

    @Then("목록에서 {string} 작품이 골라져 있다")
    fun assertSelected(title: String) {
        waitFor(selectedItem(title), useUnmergedTree = true)
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag(GALLERY_SELECTED_ITEM_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes().size
        )
    }

    @Then("공개 작품 목록은 화면 왼쪽에 있고 경로 지도는 목록 오른쪽에 그려진다")
    fun assertListLayout() {
        assertLeftOverlayWithMap(GALLERY_OVERLAY_TAG)
    }

    @Then("목록의 {string} 작품에 {string}가 표시된다")
    @Then("목록의 {string} 작품에 {string}이 표시된다")
    fun assertListItemShows(title: String, text: String) {
        val item = listItem(title) and hasAnyDescendant(hasText(text))
        waitFor(item, useUnmergedTree = true)
        composeRule.onNode(item, useUnmergedTree = true).assertIsDisplayed()
    }

    @Then("목록의 {string} 작품에 경로 썸네일이 표시된다")
    fun assertRouteThumbnail(title: String) {
        waitFor(
            listItem(
                title
            ) and hasAnyDescendant(hasContentDescription(ROUTE_THUMBNAIL_DESCRIPTION)),
            useUnmergedTree = true
        )
    }

    @Then("목록에서 {string} 작품이 {string} 작품보다 위에 있다")
    fun assertListOrder(upper: String, lower: String) {
        waitFor(listItem(upper), useUnmergedTree = true)
        waitFor(listItem(lower), useUnmergedTree = true)
        val upperBounds = composeRule.onNode(listItem(upper), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val lowerBounds = composeRule.onNode(listItem(lower), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "$upper $upperBounds, $lower $lowerBounds",
            upperBounds.bottom <= lowerBounds.top
        )
    }

    @Then("목록 맨 위 작품의 표 수가 표시된다")
    fun assertFirstVotesShown() {
        // 가장 최근 작품이 맨 위다.
        val first = listItem("작품 20") and hasAnyDescendant(hasText("0표"))
        waitFor(first, useUnmergedTree = true)
    }

    @Then("표 수는 화면에 보인 작품만 조회했다")
    fun assertOnlyVisibleCounted() {
        composeRule.waitForIdle()
        val requested = store.voteCountRequests.toSet()
        val shown = composeRule.onAllNodes(listItem(""), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .size
        val total = store.publishedCount
        assertTrue("조회 ${requested.size}개, 전체 $total", requested.size in 1 until total)
        assertTrue("조회 ${requested.size}개, 구성된 항목 $shown", requested.size <= shown)
        val loading = composeRule.onAllNodes(
            hasTestTag(GALLERY_ITEM_VOTES_TAG) and hasText(GALLERY_VOTES_LOADING),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertTrue("표 수를 기다리는 항목 ${loading.size}개", loading.isEmpty())
    }

    @Then("작품 오버레이에 {string}가 표시된다")
    @Then("작품 오버레이에 {string}이 표시된다")
    fun assertOverlayShows(text: String) {
        val node = hasText(text) and hasAnyAncestor(hasTestTag(ARTWORK_DETAIL_OVERLAY_TAG))
        waitFor(node, useUnmergedTree = true)
        composeRule.onNode(node, useUnmergedTree = true).assertIsDisplayed()
    }

    @Then("작품 오버레이는 화면 왼쪽에 있고 경로 지도는 오버레이 오른쪽에 그려진다")
    fun assertOverlayLayout() {
        assertLeftOverlayWithMap(ARTWORK_DETAIL_OVERLAY_TAG)
    }

    private fun assertLeftOverlayWithMap(overlayTag: String) {
        val map = hasText(FAKE_MAP_PREFIX, substring = true)
        waitFor(map)
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val overlay = composeRule.onNodeWithTag(overlayTag)
            .getUnclippedBoundsInRoot()
        val notice = composeRule.onNode(map).getUnclippedBoundsInRoot()
        assertTrue("오버레이 $overlay, 화면 $root", overlay.right <= (root.left + root.right) / 2)
        assertTrue("오버레이 $overlay, 지도 안내 $notice", notice.left >= overlay.right)
    }

    @Then("서버의 {string} 작품에 내 투표가 있다")
    fun assertVoted(artworkId: String) {
        val uid = requireNotNull(fakeContainer.voterUid)
        assertTrue(uid in requireNotNull(store.stored(artworkId)).voters)
    }

    @Then("서버의 {string} 작품에 내 투표가 없다")
    fun assertNotVoted(artworkId: String) {
        val uid = requireNotNull(fakeContainer.voterUid)
        assertTrue(uid !in requireNotNull(store.stored(artworkId)).voters)
    }

    /** 목록에서 [title] 글자를 가진 누를 수 있는 항목. 빈 글자면 모든 항목이다. 병합하지 않은 트리에서 찾는다. */
    private fun listItem(title: String): SemanticsMatcher {
        val inList = hasAnyAncestor(hasTestTag(GALLERY_LIST_TAG))
        val item =
            hasClickAction() and inList and hasAnyDescendant(hasTestTag(GALLERY_ITEM_VOTES_TAG))
        return if (title.isEmpty()) item else item and hasAnyDescendant(hasText(title))
    }

    private fun selectedItem(title: String): SemanticsMatcher =
        hasTestTag(GALLERY_SELECTED_ITEM_TAG) and hasAnyDescendant(hasText(title))

    private fun waitFor(matcher: SemanticsMatcher, useUnmergedTree: Boolean = false) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
