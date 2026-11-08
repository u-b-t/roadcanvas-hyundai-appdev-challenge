package kr.roadcanvas.app.mydrawings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyDrawingsPublishTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeCompletedDrawingRepository()
    private val store = FakeArtworkStore()
    private val publisher = ArtworkPublisher(
        FakeArtworkPublicationRepository(),
        store,
        FakeArtworkAuthorProvider(),
        newArtworkId = { "artwork-1" }
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun saveFreeDrawing(name: String?): Long = (
        repository.save(
            CompletedDrawing(
                mode = DrawingMode.FREE_DRAWING,
                completedAtMillis = 60_000,
                path = listOf(RoutePoint(37.5, 127.0, 0L), RoutePoint(37.6, 127.0, 1_000L)),
                score = 0,
                metrics = emptyMap(),
                distanceMeters = 100.0,
                durationMillis = 60_000,
                name = name
            )
        ) as StorageResult.Success
        ).value

    @Test
    fun `공개 중인 작품은 삭제하지 않고 비공개로 바꾸면 삭제할 수 있다`() = runTest(dispatcher) {
        saveFreeDrawing("남산 하트")
        val viewModel = MyDrawingsViewModel(repository, publisher)
        viewModel.requestPublish()
        viewModel.confirmPublish()
        assertEquals(PublishStatus.PUBLISHED, viewModel.publishState.value?.status)

        viewModel.requestDelete()
        assertFalse(viewModel.state.value.deleteConfirmVisible)

        viewModel.unpublish()
        viewModel.requestDelete()
        assertTrue(viewModel.state.value.deleteConfirmVisible)
    }

    @Test
    fun `상세에서 이름을 바꾸면 공개 작품 이름도 바꾼다`() = runTest(dispatcher) {
        saveFreeDrawing("남산 하트")
        val viewModel = MyDrawingsViewModel(repository, publisher)
        viewModel.requestPublish()
        viewModel.confirmPublish()

        viewModel.startRename()
        viewModel.saveName("남산 별")

        assertEquals("남산 별", store.artwork("artwork-1")?.draft?.title)
    }
}
