package kr.roadcanvas.app.gallery

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.gallery.fake.FakePublicArtworkStore
import kr.roadcanvas.app.gallery.fake.testServerArtwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublicGalleryTest {
    private val store = FakePublicArtworkStore()
    private var uid: String? = "me"
    private val gallery = PublicGallery(store, currentUid = { uid })

    @Test
    fun `공개 작품만 최신순으로 페이지를 나눠 읽는다`() = runTest {
        (1..5).forEach { store.put(testServerArtwork("a$it", createdAtMillis = it * 1_000L)) }
        store.setPublished("a4", published = false)

        val first = (gallery.page(after = null, limit = 2) as GalleryResult.Success).value
        assertEquals(listOf("a5", "a3"), first.items.map { it.id })
        val second = (gallery.page(first.next, limit = 2) as GalleryResult.Success).value
        assertEquals(listOf("a2", "a1"), second.items.map { it.id })
        val last = (gallery.page(second.next, limit = 2) as GalleryResult.Success).value
        assertEquals(emptyList<PublicArtwork>(), last.items)
        assertNull(last.next)
    }

    @Test
    fun `상세는 경로, 표 수와 본인 투표 여부를 함께 읽는다`() = runTest {
        store.put(testServerArtwork("a", pointCount = 25, voters = setOf("me", "other")))

        val detail = (gallery.detail("a") as GalleryResult.Success).value

        assertEquals(25, detail.route.size)
        assertEquals(2L, detail.votes)
        assertEquals(true, detail.voted)
    }

    @Test
    fun `인증 전이면 본인 투표 여부를 모르는 채 상세를 보여준다`() = runTest {
        store.put(testServerArtwork("a"))
        uid = null

        val detail = (gallery.detail("a") as GalleryResult.Success).value

        assertNull(detail.voted)
        assertEquals(0L, detail.votes)
        assertEquals(GalleryResult.Failure(GalleryFailure.AUTH), gallery.vote("a"))
    }

    @Test
    fun `비공개로 바뀐 작품의 상세와 투표는 비공개 실패다`() = runTest {
        store.put(testServerArtwork("a"))
        store.setPublished("a", published = false)

        assertEquals(GalleryResult.Failure(GalleryFailure.NOT_PUBLISHED), gallery.detail("a"))
        assertEquals(GalleryResult.Failure(GalleryFailure.NOT_PUBLISHED), gallery.vote("a"))
    }

    @Test
    fun `청크가 빠진 작품의 상세는 경로 오류다`() = runTest {
        val artwork = testServerArtwork("a", pointCount = 25)
        store.put(artwork.copy(chunks = artwork.chunks.drop(1)))

        assertEquals(GalleryResult.Failure(GalleryFailure.INVALID_ROUTE), gallery.detail("a"))
    }

    @Test
    fun `투표하기 전에 본인 투표를 확인하고 투표 문서를 만든다`() = runTest {
        store.put(testServerArtwork("a", voters = setOf("other")))

        val status = (gallery.vote("a") as GalleryResult.Success).value

        assertEquals(VoteStatus(voted = true, votes = 2), status)
        assertEquals(listOf("hasVoted:a", "vote:a", "voteCount:a"), store.requests)
        assertEquals(mapOf("a" to 2L), gallery.voteChanges.value)
    }

    @Test
    fun `응답만 잃은 투표를 다시 하면 중복으로 만들지 않고 투표한 상태가 된다`() = runTest {
        store.put(testServerArtwork("a"))
        store.loseNextVoteResponse = true

        assertEquals(GalleryResult.Failure(GalleryFailure.NETWORK), gallery.vote("a"))
        store.requests.clear()
        val retried = (gallery.vote("a") as GalleryResult.Success).value

        assertEquals(VoteStatus(voted = true, votes = 1), retried)
        assertEquals(listOf("hasVoted:a", "voteCount:a"), store.requests)
    }

    @Test
    fun `투표를 취소하면 본인 투표 문서를 지운다`() = runTest {
        store.put(testServerArtwork("a", voters = setOf("me", "other")))

        val status = (gallery.unvote("a") as GalleryResult.Success).value

        assertEquals(VoteStatus(voted = false, votes = 1), status)
        assertEquals(setOf("other"), store.stored("a")?.voters)
    }

    @Test
    fun `연결에 실패하면 네트워크 실패다`() = runTest {
        store.put(testServerArtwork("a"))
        store.networkFailure = true

        assertEquals(GalleryResult.Failure(GalleryFailure.NETWORK), gallery.page(after = null))
        assertEquals(GalleryResult.Failure(GalleryFailure.NETWORK), gallery.voteCount("a"))
        assertEquals(GalleryResult.Failure(GalleryFailure.NETWORK), gallery.vote("a"))
    }
}
