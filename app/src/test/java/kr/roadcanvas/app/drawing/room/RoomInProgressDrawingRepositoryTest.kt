package kr.roadcanvas.app.drawing.room

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.InProgressDrawing
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomInProgressDrawingRepositoryTest {
    private val database = DrawingDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val storage = database.toStorage()
    private val repository = storage.inProgress

    private val points = listOf(
        RoutePoint(37.5665, 126.978, 1_000),
        RoutePoint(37.5666, 126.9781, 2_000),
        RoutePoint(37.5667, 126.978, 3_000)
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `시작 시각과 추가한 경로 점을 순서대로 불러온다`() = runTest {
        repository.start(DrawingMode.CIRCLE, startedAtMillis = 500).value()
        points.forEach { repository.appendPoints(listOf(it)).value() }

        assertEquals(
            InProgressDrawing(DrawingMode.CIRCLE, startedAtMillis = 500, path = points),
            repository.load().value()
        )
    }

    @Test
    fun `경로 점을 추가해도 이미 저장한 점은 다시 쓰지 않는다`() = runTest {
        repository.start(DrawingMode.CIRCLE, startedAtMillis = 500).value()
        repository.appendPoints(points.take(2)).value()
        val before = database.inProgressDrawingDao().points()

        repository.appendPoints(points.drop(2)).value()

        val after = database.inProgressDrawingDao().points()
        assertEquals(before, after.take(2))
        assertEquals(3, after.size)
    }

    @Test
    fun `진행 중 기록은 완료 기록 목록에 나타나지 않는다`() = runTest {
        repository.start(DrawingMode.CIRCLE, startedAtMillis = 500).value()
        repository.appendPoints(points).value()

        assertEquals(emptyList<Any>(), storage.completed.summaries().value())
        assertNull(storage.completed.bestScore(DrawingMode.CIRCLE).value())
    }

    @Test
    fun `삭제하면 진행 중 기록이 없다`() = runTest {
        repository.start(DrawingMode.CIRCLE, startedAtMillis = 500).value()
        repository.appendPoints(points).value()

        repository.clear().value()

        assertNull(repository.load().value())
        assertEquals(emptyList<Any>(), database.inProgressDrawingDao().points())
    }

    @Test
    fun `새로 시작하면 이전 경로를 지운다`() = runTest {
        repository.start(DrawingMode.CIRCLE, startedAtMillis = 500).value()
        repository.appendPoints(points).value()

        repository.start(DrawingMode.CIRCLE, startedAtMillis = 9_000).value()

        assertEquals(
            InProgressDrawing(DrawingMode.CIRCLE, startedAtMillis = 9_000, path = emptyList()),
            repository.load().value()
        )
    }

    @Test
    fun `빈 저장소에는 진행 중 기록이 없다`() = runTest {
        assertNull(repository.load().value())
        assertEquals(StorageResult.Success(Unit), repository.clear())
    }

    @Test
    fun `시작하지 않은 도전에 경로 점을 추가하면 실패하고 저장하지 않는다`() = runTest {
        assertTrue(repository.appendPoints(points) is StorageResult.Failure)
        assertEquals(emptyList<Any>(), database.inProgressDrawingDao().points())
    }

    @Test
    fun `저장된 값을 읽지 못하면 실패 결과를 돌려준다`() = runTest {
        database.inProgressDrawingDao()
            .replaceDrawing(InProgressDrawingEntity(mode = "unknown", startedAtMillis = 1))

        assertTrue(repository.load() is StorageResult.Failure)
    }
}
