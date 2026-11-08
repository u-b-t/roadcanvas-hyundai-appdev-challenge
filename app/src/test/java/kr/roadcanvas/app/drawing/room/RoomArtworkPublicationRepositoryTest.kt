package kr.roadcanvas.app.drawing.room

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.artwork.ArtworkPublication
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomArtworkPublicationRepositoryTest {
    private val database = DrawingDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val storage = database.toStorage()

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun saveDrawing(): Long {
        val drawing = CompletedDrawing(
            mode = DrawingMode.FREE_DRAWING,
            completedAtMillis = 1_000,
            path = emptyList(),
            score = 0,
            metrics = emptyMap(),
            distanceMeters = 100.0,
            durationMillis = 1_000,
            name = "남산 하트"
        )
        return (storage.completed.save(drawing) as StorageResult.Success).value
    }

    private suspend fun find(id: Long) =
        (storage.publications.find(id) as StorageResult.Success).value

    @Test
    fun `공개 기록을 저장하고 같은 완료 기록은 상태만 바꾼다`() = runTest {
        val id = saveDrawing()
        assertNull(find(id))

        storage.publications.save(ArtworkPublication(id, "uuid-1", PublishStatus.PUBLISHING))
        storage.publications.save(ArtworkPublication(id, "uuid-1", PublishStatus.PUBLISHED))

        assertEquals(ArtworkPublication(id, "uuid-1", PublishStatus.PUBLISHED), find(id))
    }

    @Test
    fun `완료 기록을 지우면 공개 기록도 지워진다`() = runTest {
        val id = saveDrawing()
        storage.publications.save(ArtworkPublication(id, "uuid-1", PublishStatus.FAILED))

        storage.completed.delete(id)

        assertNull(find(id))
    }

    @Test
    fun `없는 완료 기록의 공개 기록은 저장하지 못한다`() = runTest {
        val result = storage.publications.save(
            ArtworkPublication(99, "uuid-1", PublishStatus.FAILED)
        )

        assertTrue(result is StorageResult.Failure)
    }
}
