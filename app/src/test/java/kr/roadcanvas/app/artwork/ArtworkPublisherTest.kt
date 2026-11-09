package kr.roadcanvas.app.artwork

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPublisherTest {
    private val publications = FakeArtworkPublicationRepository()
    private val store = FakeArtworkStore()
    private val author = FakeArtworkAuthorProvider()
    private var ids = 0
    private val publisher = ArtworkPublisher(publications, store, author, { "artwork-${++ids}" })

    private suspend fun saved(drawingId: Long = 1L) =
        (publications.find(drawingId) as StorageResult.Success).value

    private suspend fun status(drawingId: Long = 1L) =
        (publisher.status(drawingId) as StorageResult.Success).value

    @Test
    fun `DRAFT 문서, 청크, 확인, PUBLISHED 순서로 등록하고 공개됨으로 남긴다`() = runTest {
        val result = publisher.publish(freeDrawing(name = "  남산 하트  ", pointCount = 1_001))

        assertEquals(ArtworkResult.Success, result)
        assertEquals(
            listOf(
                "saveDraft",
                "saveChunk",
                "saveChunk",
                "saveChunk",
                "chunkIndices",
                "setVisibility"
            ),
            store.requests
        )
        val artwork = store.artwork("artwork-1")!!
        assertEquals("test-uid", artwork.ownerUid)
        assertEquals(ArtworkVisibility.PUBLISHED, artwork.visibility)
        assertEquals(
            ArtworkDraft("드라이버-test", "남산 하트", 123.4, 60_000, pointChunkCount = 3),
            artwork.draft
        )
        assertEquals(setOf(0, 1, 2), artwork.chunks.keys)
        assertEquals(ArtworkPublication(1L, "artwork-1", PublishStatus.PUBLISHED), saved())
        assertEquals(PublishStatus.PUBLISHED, status())
    }

    @Test
    fun `공개를 시도한 적이 없으면 미공개다`() = runTest {
        assertEquals(PublishStatus.UNPUBLISHED, status())
    }

    @Test
    fun `네트워크 실패면 실패로 남기고 같은 작품 ID로 다시 등록한다`() = runTest {
        store.networkFailure = true

        val failed = publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Failure(PublishFailure.NETWORK), failed)
        assertEquals(ArtworkPublication(1L, "artwork-1", PublishStatus.FAILED), saved())
        assertEquals(PublishStatus.FAILED, status())

        store.networkFailure = false
        assertEquals(ArtworkResult.Success, publisher.publish(freeDrawing()))

        assertEquals(setOf("artwork-1"), store.all.keys)
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
        assertEquals(PublishStatus.PUBLISHED, saved()?.status)
    }

    @Test
    fun `작성자를 확인하지 못하면 서버에 요청하지 않고 인증 실패다`() = runTest {
        author.failure = IllegalStateException("로그인 실패")

        val result = publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Failure(PublishFailure.AUTH), result)
        assertTrue(store.requests.isEmpty())
        assertEquals(ArtworkPublication(1L, "artwork-1", PublishStatus.FAILED), saved())
    }

    @Test
    fun `일부 청크 저장이 실패하면 공개하지 않고 다시 시도하면 모든 청크를 저장한다`() = runTest {
        store.failChunkAt = 1

        val failed = publisher.publish(freeDrawing(pointCount = 1_200))

        assertEquals(ArtworkResult.Failure(PublishFailure.NETWORK), failed)
        assertEquals(ArtworkVisibility.DRAFT, store.artwork("artwork-1")?.visibility)
        assertEquals(setOf(0), store.artwork("artwork-1")?.chunks?.keys)

        assertEquals(ArtworkResult.Success, publisher.publish(freeDrawing(pointCount = 1_200)))
        assertEquals(setOf(0, 1, 2), store.artwork("artwork-1")?.chunks?.keys)
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
    }

    @Test
    fun `저장된 청크 개수가 맞지 않으면 공개하지 않는다`() = runTest {
        store.dropChunkAt = 2

        val failed = publisher.publish(freeDrawing(pointCount = 1_200))

        assertEquals(ArtworkResult.Failure(PublishFailure.INCOMPLETE), failed)
        assertEquals(ArtworkVisibility.DRAFT, store.artwork("artwork-1")?.visibility)
        assertTrue("setVisibility" !in store.requests)
        assertEquals(PublishStatus.FAILED, saved()?.status)

        assertEquals(ArtworkResult.Success, publisher.publish(freeDrawing(pointCount = 1_200)))
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
    }

    @Test
    fun `이름이 없거나 자유그림이 아니면 공개하지 않는다`() = runTest {
        assertEquals(
            ArtworkResult.Failure(PublishFailure.INVALID),
            publisher.publish(freeDrawing(name = null))
        )
        assertEquals(
            ArtworkResult.Failure(PublishFailure.INVALID),
            publisher.publish(freeDrawing(name = "   "))
        )
        assertEquals(
            ArtworkResult.Failure(PublishFailure.INVALID),
            publisher.publish(freeDrawing(name = "동그라미", mode = DrawingMode.CIRCLE))
        )
        assertTrue(store.requests.isEmpty())
        assertNull(saved())
    }

    @Test
    fun `닉네임이 공개 규칙에 맞지 않으면 문서를 만들지 않는다`() = runTest {
        author.author = ArtworkAuthor("test-uid", "가".repeat(ARTWORK_NICKNAME_MAX_LENGTH + 1))

        val result = publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Failure(PublishFailure.INVALID), result)
        assertTrue(store.requests.isEmpty())
    }

    @Test
    fun `기기 기록을 저장하지 못하면 서버에 요청하지 않는다`() = runTest {
        publications.failure = IOException("disk")

        val result = publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Failure(PublishFailure.STORAGE), result)
        assertTrue(store.requests.isEmpty())
    }

    @Test
    fun `등록 중으로 저장된 채 요청이 없으면 실패로 본다`() = runTest {
        publications.save(ArtworkPublication(1L, "artwork-9", PublishStatus.PUBLISHING))

        assertEquals(PublishStatus.FAILED, status())
    }

    @Test
    fun `공개한 작품을 비공개로 되돌리고 다시 공개하면 같은 작품 ID를 쓴다`() = runTest {
        publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Success, publisher.unpublish(1L))
        assertEquals(ArtworkVisibility.DRAFT, store.artwork("artwork-1")?.visibility)
        assertEquals(PublishStatus.UNPUBLISHED, status())

        assertEquals(ArtworkResult.Success, publisher.publish(freeDrawing()))
        assertEquals(setOf("artwork-1"), store.all.keys)
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
    }

    @Test
    fun `비공개 전환에 실패하면 공개 상태를 유지한다`() = runTest {
        publisher.publish(freeDrawing())
        store.networkFailure = true

        assertEquals(ArtworkResult.Failure(PublishFailure.NETWORK), publisher.unpublish(1L))
        assertEquals(PublishStatus.PUBLISHED, status())
        store.networkFailure = false
        assertEquals(ArtworkVisibility.PUBLISHED, store.artwork("artwork-1")?.visibility)
    }

    @Test
    fun `공개 중인 작품의 이름을 바꾸면 title에 반영한다`() = runTest {
        publisher.publish(freeDrawing())

        assertEquals(ArtworkResult.Success, publisher.syncTitle(1L, " 남산 별 "))

        assertEquals("남산 별", store.artwork("artwork-1")?.draft?.title)
    }

    @Test
    fun `공개하지 않은 작품의 이름 변경은 서버에 요청하지 않는다`() = runTest {
        assertEquals(ArtworkResult.Success, publisher.syncTitle(1L, "남산 별"))
        assertTrue(store.requests.isEmpty())
    }

    @Test
    fun `공개 작품의 이름 반영이 실패하면 실패 이유를 돌려준다`() = runTest {
        publisher.publish(freeDrawing())
        store.networkFailure = true

        assertEquals(
            ArtworkResult.Failure(PublishFailure.NETWORK),
            publisher.syncTitle(1L, "남산 별")
        )
    }

    @Test
    fun `이름을 바꾼 뒤 다시 공개하면 바뀐 이름으로 등록한다`() = runTest {
        publisher.publish(freeDrawing())
        publisher.unpublish(1L)

        publisher.publish(freeDrawing(name = "남산 별"))

        assertEquals("남산 별", store.artwork("artwork-1")?.draft?.title)
    }
}
