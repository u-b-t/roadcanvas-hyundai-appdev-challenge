package kr.roadcanvas.app.drawing.room

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.territory.CaptureSyncStatus
import kr.roadcanvas.app.territory.StoredTerritoryCapture
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomTerritoryCaptureRepositoryTest {
    private val database = DrawingDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val captures = database.toStorage().territoryCaptures

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun capture(id: String, drawingId: Long, createdAt: Long) = StoredTerritoryCapture(
        captureId = id,
        drawingId = drawingId,
        cells = listOf("wydm9qy", "wydm9qz"),
        createdAtMillis = createdAt,
        status = CaptureSyncStatus.PENDING
    )

    @Test
    fun `미제출 획득을 칸과 함께 저장하고 만든 순서로 읽는다`() = runTest {
        captures.save(capture("b", drawingId = 2, createdAt = 200))
        captures.save(capture("a", drawingId = 1, createdAt = 100))

        val pending = (captures.pending() as StorageResult.Success).value
        assertEquals(listOf("a", "b"), pending.map { it.captureId })
        assertEquals(listOf("wydm9qy", "wydm9qz"), pending.first().cells)
        assertNull(pending.first().outcome)
    }

    @Test
    fun `반영 결과를 저장하면 미제출 목록에서 빠지고 기록으로 찾는다`() = runTest {
        val outcome = TerritoryCaptureOutcome(newCells = 1, stolenCells = 1, ownCells = 0)
        captures.save(capture("a", drawingId = 1, createdAt = 100))
        captures.save(
            capture("a", drawingId = 1, createdAt = 100)
                .copy(status = CaptureSyncStatus.SUBMITTED, outcome = outcome)
        )

        assertEquals(
            emptyList<StoredTerritoryCapture>(),
            (captures.pending() as StorageResult.Success).value
        )
        val found = (captures.findByDrawing(1) as StorageResult.Success).value
        assertEquals(CaptureSyncStatus.SUBMITTED, found?.status)
        assertEquals(outcome, found?.outcome)
        assertNull((captures.findByDrawing(2) as StorageResult.Success).value)
    }
}
