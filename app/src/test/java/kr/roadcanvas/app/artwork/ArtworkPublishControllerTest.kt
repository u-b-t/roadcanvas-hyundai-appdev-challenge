package kr.roadcanvas.app.artwork

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.artwork.fake.FakeArtworkAuthorProvider
import kr.roadcanvas.app.artwork.fake.FakeArtworkPublicationRepository
import kr.roadcanvas.app.artwork.fake.FakeArtworkStore
import kr.roadcanvas.app.drawing.DrawingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkPublishControllerTest {
    private val store = FakeArtworkStore()
    private val author = FakeArtworkAuthorProvider()
    private val publisher =
        ArtworkPublisher(FakeArtworkPublicationRepository(), store, author, { "artwork-1" })

    private fun test(
        block: suspend kotlinx.coroutines.test.TestScope.(ArtworkPublishController) -> Unit
    ) = runTest(UnconfinedTestDispatcher()) {
        block(ArtworkPublishController(publisher, backgroundScope))
    }

    @Test
    fun `자유그림이 아니면 공개 상태를 보이지 않는다`() = test { controller ->
        controller.show(freeDrawing(mode = DrawingMode.CIRCLE))

        assertNull(controller.state.value)
    }

    @Test
    fun `이름이 없는 자유그림은 미공개이고 공개할 수 없다`() = test { controller ->
        controller.show(freeDrawing(name = null))
        controller.requestPublish()

        val state = controller.state.value!!
        assertEquals(PublishStatus.UNPUBLISHED, state.status)
        assertFalse(state.canPublish)
        assertFalse(state.consentVisible)
    }

    @Test
    fun `동의 안내를 취소하면 공개하지 않는다`() = test { controller ->
        controller.show(freeDrawing())
        controller.requestPublish()
        assertTrue(controller.state.value!!.consentVisible)

        controller.cancelConsent()

        assertFalse(controller.state.value!!.consentVisible)
        assertEquals(PublishStatus.UNPUBLISHED, controller.state.value!!.status)
        assertTrue(store.requests.isEmpty())
    }

    @Test
    fun `동의 안내를 열지 않으면 동의해도 공개하지 않는다`() = test { controller ->
        controller.show(freeDrawing())

        controller.confirmPublish()

        assertTrue(store.requests.isEmpty())
    }

    @Test
    fun `동의하면 공개하고 공개됨을 보인다`() = test { controller ->
        controller.show(freeDrawing())
        controller.requestPublish()

        controller.confirmPublish()

        assertEquals(PublishStatus.PUBLISHED, controller.state.value!!.status)
        assertFalse(controller.state.value!!.consentVisible)
    }

    @Test
    fun `실패하면 이유를 보이고 다시 시도하면 공개된다`() = test { controller ->
        store.networkFailure = true
        controller.show(freeDrawing())
        controller.requestPublish()
        controller.confirmPublish()

        assertEquals(PublishStatus.FAILED, controller.state.value!!.status)
        assertEquals(PublishFailure.NETWORK, controller.state.value!!.failure)

        store.networkFailure = false
        controller.retry()

        assertEquals(PublishStatus.PUBLISHED, controller.state.value!!.status)
        assertNull(controller.state.value!!.failure)
    }

    @Test
    fun `비공개로 바꾸면 미공개로 보이고 실패하면 공개됨과 이유를 보인다`() = test { controller ->
        controller.show(freeDrawing())
        controller.requestPublish()
        controller.confirmPublish()

        store.networkFailure = true
        controller.unpublish()
        assertEquals(PublishStatus.PUBLISHED, controller.state.value!!.status)
        assertEquals(PublishFailure.NETWORK, controller.state.value!!.unpublishFailure)

        store.networkFailure = false
        controller.unpublish()
        assertEquals(PublishStatus.UNPUBLISHED, controller.state.value!!.status)
        assertNull(controller.state.value!!.unpublishFailure)
    }

    @Test
    fun `공개 중에 이름을 바꾸면 작품 이름에 반영하고 실패를 알린다`() = test { controller ->
        controller.show(freeDrawing())
        controller.requestPublish()
        controller.confirmPublish()

        controller.onRenamed("남산 별")
        assertEquals("남산 별", store.artwork("artwork-1")?.draft?.title)
        assertNull(controller.state.value!!.titleSyncFailure)

        store.networkFailure = true
        controller.onRenamed("남산 달")
        assertEquals(PublishFailure.NETWORK, controller.state.value!!.titleSyncFailure)
    }

    @Test
    fun `이름을 붙이면 공개할 수 있다`() = test { controller ->
        controller.show(freeDrawing(name = null))

        controller.onRenamed("남산 하트")

        assertTrue(controller.state.value!!.canPublish)
    }

    @Test
    fun `저장된 공개 상태를 다시 보여준다`() = test { controller ->
        publisher.publish(freeDrawing())

        controller.show(freeDrawing())

        assertEquals(PublishStatus.PUBLISHED, controller.state.value!!.status)
    }
}
