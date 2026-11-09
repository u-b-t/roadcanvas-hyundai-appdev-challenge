package kr.roadcanvas.app.test

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.artwork.ARTWORK_STATUS_TAG
import kr.roadcanvas.app.artwork.ArtworkResult
import kr.roadcanvas.app.artwork.ArtworkVisibility
import kr.roadcanvas.app.artwork.fake.FakeArtwork
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private const val WAIT_TIMEOUT_MILLIS = 5_000L

/**
 * 작품 공개 시나리오. 서버와 익명 계정은 대체 구현으로 바꿔 상태 전환과 오류를 확인하고,
 * 화면은 결과 화면과 내 그림 상세의 실제 흐름으로 조작한다.
 */
class ArtworkPublishSteps(private val ruleHolder: ComposeRuleHolder) {
    private val composeRule get() = ruleHolder.composeRule
    private val fakeContainer
        get() = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
            .container as FakeAppContainer
    private val store get() = fakeContainer.artworkStore

    @Given("이름이 {string}이고 경로 점 {int}개인 자유그림 기록이 있다")
    fun saveNamedFreeDrawing(name: String, pointCount: Int) {
        saveFreeDrawing(name, pointCount)
    }

    @Given("이름이 {string}인 자유그림 기록을 공개해 두었다")
    fun savePublishedFreeDrawing(name: String) {
        val drawing = saveFreeDrawing(name, pointCount = 3)
        val result = runBlocking { fakeContainer.artworkPublisher.publish(drawing) }
        assertEquals(ArtworkResult.Success, result)
        store.requests.clear()
    }

    @Given("작품 서버 연결이 실패하도록 설정되어 있다")
    fun failNetwork() {
        store.networkFailure = true
    }

    @When("작품 서버 연결 실패를 해제한다")
    fun restoreNetwork() {
        store.networkFailure = false
    }

    @Given("익명 계정 확인이 실패하도록 설정되어 있다")
    fun failAuth() {
        fakeContainer.artworkAuthorProvider.failure = IllegalStateException("익명 인증 실패")
    }

    @Given("작품 서버가 경로 청크 하나를 저장하지 않도록 설정되어 있다")
    fun dropChunk() {
        store.dropChunkAt = 1
    }

    @Then("공개 상태는 {string}다")
    @Then("공개 상태는 {string}이다")
    fun assertStatus(label: String) {
        val status = hasTestTag(ARTWORK_STATUS_TAG) and hasText(label)
        waitFor(status)
        scrollIntoView(status)
        composeRule.onNode(status).assertIsDisplayed()
    }

    @Then("공개 안내에 {string}가 표시된다")
    @Then("공개 안내에 {string}이 표시된다")
    fun assertMessage(text: String) {
        val message = hasText(text)
        waitFor(message)
        scrollIntoView(message)
        composeRule.onNode(message).assertIsDisplayed()
    }

    @Then("공개 동의 안내에 {string}가 표시된다")
    @Then("공개 동의 안내에 {string}이 표시된다")
    fun assertConsent(text: String) {
        val message = hasText(text, substring = true) and hasAnyAncestor(isDialog())
        waitFor(message)
        composeRule.onNode(message).assertIsDisplayed()
    }

    @Then("서버에 작품이 없다")
    fun assertNoArtwork() {
        composeRule.waitForIdle()
        assertTrue("서버 작품: ${store.all}", store.all.isEmpty())
    }

    @Then("서버에 작품 {int}개가 있다")
    fun assertArtworkCount(count: Int) {
        assertEquals(count, store.all.size)
    }

    @Then("서버 작품은 공개 상태이고 이름 {string}, 닉네임 {string}이며 경로 청크가 빠짐없다")
    fun assertPublishedArtwork(title: String, nickname: String) {
        waitUntil { onlyArtwork()?.visibility == ArtworkVisibility.PUBLISHED }
        val artwork = checkNotNull(onlyArtwork())
        assertEquals(title, artwork.draft.title)
        assertEquals(nickname, artwork.draft.nickname)
        assertEquals(FakeArtworkAuthorProvider.DEFAULT_AUTHOR.uid, artwork.ownerUid)
        assertEquals((0 until artwork.draft.pointChunkCount).toSet(), artwork.chunks.keys)
        assertTrue(artwork.draft.pointChunkCount > 0)
    }

    @Then("서버 작품은 비공개 상태다")
    fun assertDraftArtwork() {
        waitUntil { onlyArtwork()?.visibility == ArtworkVisibility.DRAFT }
    }

    @Then("서버 작품의 경로 청크는 {int}개이고 순서대로 빠짐없다")
    fun assertChunks(count: Int) {
        val artwork = checkNotNull(onlyArtwork())
        assertEquals(count, artwork.draft.pointChunkCount)
        assertEquals((0 until count).toSet(), artwork.chunks.keys)
        // 청크를 index 순서로 이으면 기록한 경로의 시각이 처음부터 차례로 이어진다.
        val times = (0 until count).flatMap { index ->
            Regex("\"recordedAtMillis\":(\\d+)").findAll(artwork.chunks.getValue(index))
                .map { it.groupValues[1].toLong() }.toList()
        }
        assertEquals(times.sorted(), times)
        assertEquals(drawingPath().size, times.size)
    }

    @Then("서버 작품의 ID는 기기 기록의 작품 ID와 같다")
    fun assertSameArtworkId() {
        val publication = runBlocking {
            fakeContainer.artworkPublicationRepository.find(savedDrawingId())
        }
        val artworkId = (publication as StorageResult.Success).value?.artworkId
        assertEquals(setOf(artworkId), store.all.keys)
    }

    @Then("서버 작품의 이름은 {string}이다")
    @Then("서버 작품의 이름은 {string}다")
    fun assertArtworkTitle(title: String) {
        waitUntil { onlyArtwork()?.draft?.title == title }
    }

    @Then("완료 기록 {int}개가 기기에 남아 있다")
    fun assertLocalDrawings(count: Int) {
        val summaries = runBlocking { fakeContainer.completedDrawingRepository.summaries() }
        assertEquals(count, (summaries as StorageResult.Success).value.size)
    }

    private fun saveFreeDrawing(name: String, pointCount: Int): CompletedDrawing {
        val completedAt = System.currentTimeMillis()
        val drawing = CompletedDrawing(
            mode = DrawingMode.FREE_DRAWING,
            completedAtMillis = completedAt,
            path = List(pointCount) { index ->
                RoutePoint(37.5665 + index * 0.00001, 126.978, completedAt - pointCount + index)
            },
            score = 0,
            metrics = emptyMap(),
            distanceMeters = 314.0,
            durationMillis = 63_000,
            name = name
        )
        val result = runBlocking { fakeContainer.completedDrawingRepository.save(drawing) }
        assertTrue("저장 결과: $result", result is StorageResult.Success)
        return drawing.copy(id = (result as StorageResult.Success).value)
    }

    private fun savedDrawingId(): Long = runBlocking {
        (fakeContainer.completedDrawingRepository.summaries() as StorageResult.Success)
            .value.single().id
    }

    private fun drawingPath(): List<RoutePoint> = runBlocking {
        val found = fakeContainer.completedDrawingRepository.find(savedDrawingId())
        checkNotNull((found as StorageResult.Success).value).path
    }

    private fun onlyArtwork(): FakeArtwork? = store.all.values.singleOrNull()

    /** 스크롤 영역 안에 있으면 보이도록 스크롤한다. */
    private fun scrollIntoView(matcher: SemanticsMatcher) {
        val scrollable = matcher and hasAnyAncestor(hasScrollAction())
        if (composeRule.onAllNodes(scrollable).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNode(matcher).performScrollTo()
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        waitUntil { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(WAIT_TIMEOUT_MILLIS, condition)
    }
}
