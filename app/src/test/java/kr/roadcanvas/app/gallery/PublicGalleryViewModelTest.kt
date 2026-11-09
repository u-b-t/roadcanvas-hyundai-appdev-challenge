package kr.roadcanvas.app.gallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.gallery.fake.FakePublicArtworkStore
import kr.roadcanvas.app.gallery.fake.testServerArtwork
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PublicGalleryViewModelTest {
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

    private val PublicGalleryUiState.loaded get() = list as GalleryListState.Loaded

    @Test
    fun `공개 작품이 없으면 빈 목록이다`() {
        assertEquals(GalleryListState.Empty, PublicGalleryViewModel(gallery).state.value.list)
    }

    @Test
    fun `목록 조회에 실패하면 이유를 알리고 다시 불러올 수 있다`() {
        store.put(testServerArtwork("a"))
        store.networkFailure = true
        val viewModel = PublicGalleryViewModel(gallery)
        assertEquals(GalleryListState.Error(GalleryFailure.NETWORK), viewModel.state.value.list)

        store.networkFailure = false
        viewModel.refresh()

        assertEquals(listOf("a"), viewModel.state.value.loaded.items.map { it.id })
    }

    @Test
    fun `목록은 표 수를 조회하지 않고 보인 항목만 한 번씩 표 수와 썸네일을 요청한다`() {
        (1..3).forEach { store.put(testServerArtwork("a$it", createdAtMillis = it * 1_000L)) }
        store.put(testServerArtwork("a1", createdAtMillis = 1_000L, voters = setOf("x", "y")))
        val viewModel = PublicGalleryViewModel(gallery)
        assertEquals(emptyList<String>(), store.voteCountRequests)

        val visible = viewModel.state.value.loaded.items.last()
        viewModel.onItemVisible(visible)
        viewModel.onItemVisible(visible)

        assertEquals(listOf("a1"), store.voteCountRequests)
        val extras = viewModel.state.value.extras.getValue("a1")
        assertEquals(2L, extras.votes)
        assertTrue(extras.thumbnail!!.size >= 2)
        assertEquals(setOf("a1"), viewModel.state.value.extras.keys)
    }

    @Test
    fun `표 수를 읽지 못하면 그 항목만 실패로 표시한다`() {
        store.put(testServerArtwork("a"))
        val viewModel = PublicGalleryViewModel(gallery)
        store.networkFailure = true

        viewModel.onItemVisible(viewModel.state.value.loaded.items.single())

        val extras = viewModel.state.value.extras.getValue("a")
        assertTrue(extras.votesFailed)
        assertNull(extras.votes)
        assertNull(extras.thumbnail)
    }

    @Test
    fun `다음 페이지를 이어 붙이고 끝 페이지면 더 보기를 없앤다`() {
        (1..25).forEach { store.put(testServerArtwork("a$it", createdAtMillis = it * 1_000L)) }
        val viewModel = PublicGalleryViewModel(gallery)
        assertEquals(PUBLIC_ARTWORK_PAGE_SIZE, viewModel.state.value.loaded.items.size)
        assertTrue(viewModel.state.value.loaded.hasMore)

        viewModel.loadMore()

        val loaded = viewModel.state.value.loaded
        assertEquals(25, loaded.items.size)
        assertEquals(25, loaded.items.map { it.id }.toSet().size)
        assertFalse(loaded.hasMore)
    }

    @Test
    fun `다음 페이지를 읽지 못하면 앞 목록을 유지하고 실패를 표시한다`() {
        (1..25).forEach { store.put(testServerArtwork("a$it", createdAtMillis = it * 1_000L)) }
        val viewModel = PublicGalleryViewModel(gallery)
        store.networkFailure = true

        viewModel.loadMore()

        val loaded = viewModel.state.value.loaded
        assertEquals(PUBLIC_ARTWORK_PAGE_SIZE, loaded.items.size)
        assertEquals(GalleryFailure.NETWORK, loaded.loadMoreFailure)
        assertTrue(loaded.hasMore)
    }

    @Test
    fun `상세에서 투표한 결과를 다시 조회하지 않고 목록 표 수에 반영한다`() = runTest {
        store.put(testServerArtwork("a"))
        val viewModel = PublicGalleryViewModel(gallery)
        viewModel.onItemVisible(viewModel.state.value.loaded.items.single())
        assertEquals(0L, viewModel.state.value.extras.getValue("a").votes)
        val countRequests = store.voteCountRequests.size

        gallery.vote("a")

        assertEquals(1L, viewModel.state.value.extras.getValue("a").votes)
        // 투표 직후 한 번만 다시 센다.
        assertEquals(countRequests + 1, store.voteCountRequests.size)
    }
}
