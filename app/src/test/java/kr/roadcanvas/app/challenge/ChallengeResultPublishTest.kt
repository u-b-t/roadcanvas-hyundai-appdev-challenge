package kr.roadcanvas.app.challenge

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.artwork.ArtworkPublisher
import kr.roadcanvas.app.artwork.ArtworkVisibility
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.drawing.fake.FakeInProgressDrawingRepository
import kr.roadcanvas.app.recording.ChallengeRecorder
import kr.roadcanvas.app.score.TestPaths
import kr.roadcanvas.app.vehicle.fake.FakeLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeResultPublishTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val inProgress = FakeInProgressDrawingRepository()
    private val completed = FakeCompletedDrawingRepository()
    private val store = FakeArtworkStore()
    private val publisher = ArtworkPublisher(
        FakeArtworkPublicationRepository(),
        store,
        FakeArtworkAuthorProvider(),
        newArtworkId = { "artwork-1" }
    )
    private val recorder = ChallengeRecorder(FakeLocationProvider(), inProgress, clock = { START })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun finish(mode: DrawingMode): ChallengeResultViewModel {
        inProgress.start(mode, START)
        inProgress.appendPoints(
            TestPaths.circle(50.0).mapIndexed { i, it ->
                RoutePoint(it.latitude, it.longitude, START + i * 1_000L)
            }
        )
        return ChallengeResultViewModel(
            startedAtMillis = START,
            endedAtMillis = START + 60_000L,
            savedStateHandle = SavedStateHandle(),
            recorder = recorder,
            inProgressRepository = inProgress,
            completedRepository = completed,
            artworkPublisher = publisher
        )
    }

    @Test
    fun `저장한 자유그림은 이름을 붙이기 전까지 미공개이고 공개할 수 없다`() = runTest(dispatcher) {
        val state = finish(DrawingMode.FREE_DRAWING).publishState.value!!

        assertEquals(PublishStatus.UNPUBLISHED, state.status)
        assertFalse(state.canPublish)
    }

    @Test
    fun `이름을 붙이고 동의하면 그 이름으로 공개한다`() = runTest(dispatcher) {
        val viewModel = finish(DrawingMode.FREE_DRAWING)

        viewModel.saveName("남산 하트")
        viewModel.requestPublish()
        assertTrue(viewModel.publishState.value!!.consentVisible)
        viewModel.confirmPublish()

        assertEquals(PublishStatus.PUBLISHED, viewModel.publishState.value!!.status)
        assertEquals("남산 하트", store.artwork("artwork-1")?.draft?.title)
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
    }

    @Test
    fun `공개한 뒤 이름을 바꾸면 공개 작품 이름도 바꾼다`() = runTest(dispatcher) {
        val viewModel = finish(DrawingMode.FREE_DRAWING)
        viewModel.saveName("남산 하트")
        viewModel.requestPublish()
        viewModel.confirmPublish()

        viewModel.saveName("남산 별")

        assertEquals("남산 별", store.artwork("artwork-1")?.draft?.title)
    }

    @Test
    fun `동그라미 챌린지 결과는 공개 상태를 보이지 않는다`() = runTest(dispatcher) {
        assertNull(finish(DrawingMode.CIRCLE).publishState.value)
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
