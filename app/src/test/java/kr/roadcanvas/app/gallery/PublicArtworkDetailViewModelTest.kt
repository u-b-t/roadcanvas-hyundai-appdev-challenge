package kr.roadcanvas.app.gallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.gallery.fake.FakePublicArtworkStore
import kr.roadcanvas.app.gallery.fake.testServerArtwork
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PublicArtworkDetailViewModelTest {
    private val store = FakePublicArtworkStore()
    private val gallery = PublicGallery(store, currentUid = { "me" })

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val PublicArtworkDetailViewModel.loaded
        get() = state.value as ArtworkDetailState.Loaded

    @Test
    fun `이미 투표한 작품은 투표한 상태로 불러온다`() {
        store.put(testServerArtwork("a", voters = setOf("me")))

        val viewModel = PublicArtworkDetailViewModel(gallery, "a")

        assertEquals(true, viewModel.loaded.detail.voted)
        assertEquals(1L, viewModel.loaded.detail.votes)
    }

    @Test
    fun `투표하고 다시 누르면 취소한다`() {
        store.put(testServerArtwork("a", voters = setOf("other")))
        val viewModel = PublicArtworkDetailViewModel(gallery, "a")

        viewModel.toggleVote()
        assertEquals(true, viewModel.loaded.detail.voted)
        assertEquals(2L, viewModel.loaded.detail.votes)

        viewModel.toggleVote()
        assertEquals(false, viewModel.loaded.detail.voted)
        assertEquals(1L, viewModel.loaded.detail.votes)
    }

    @Test
    fun `투표에 실패하면 이유를 남기고 투표하지 않은 상태를 유지한다`() {
        store.put(testServerArtwork("a"))
        val viewModel = PublicArtworkDetailViewModel(gallery, "a")
        store.networkFailure = true

        viewModel.toggleVote()

        assertEquals(GalleryFailure.NETWORK, viewModel.loaded.voteFailure)
        assertEquals(false, viewModel.loaded.detail.voted)
        assertEquals(false, viewModel.loaded.voting)
    }

    @Test
    fun `응답을 잃은 투표를 다시 누르면 투표한 상태가 되고 표는 하나만 늘어난다`() {
        store.put(testServerArtwork("a"))
        val viewModel = PublicArtworkDetailViewModel(gallery, "a")
        store.loseNextVoteResponse = true

        viewModel.toggleVote()
        assertEquals(false, viewModel.loaded.detail.voted)
        viewModel.toggleVote()

        assertEquals(true, viewModel.loaded.detail.voted)
        assertEquals(1L, viewModel.loaded.detail.votes)
        assertNull(viewModel.loaded.voteFailure)
    }

    @Test
    fun `보던 중 비공개로 바뀐 작품에 투표하면 비공개 안내로 바뀐다`() {
        store.put(testServerArtwork("a"))
        val viewModel = PublicArtworkDetailViewModel(gallery, "a")
        store.setPublished("a", published = false)

        viewModel.toggleVote()

        assertEquals(ArtworkDetailState.NotPublished, viewModel.state.value)
    }

    @Test
    fun `비공개 작품은 안내하고 연결 실패는 다시 시도할 수 있다`() {
        store.put(testServerArtwork("a"))
        store.setPublished("a", published = false)
        assertEquals(
            ArtworkDetailState.NotPublished,
            PublicArtworkDetailViewModel(gallery, "a").state.value
        )

        store.setPublished("a", published = true)
        store.networkFailure = true
        val viewModel = PublicArtworkDetailViewModel(gallery, "a")
        assertEquals(ArtworkDetailState.Error(GalleryFailure.NETWORK), viewModel.state.value)

        store.networkFailure = false
        viewModel.retry()
        assertEquals("a", viewModel.loaded.detail.artwork.id)
    }
}
