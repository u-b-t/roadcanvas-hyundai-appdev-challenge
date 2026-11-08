package kr.roadcanvas.app.drawing.room

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
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
class RoomCompletedDrawingRepositoryTest {
    private val database = DrawingDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = database.toStorage().completed

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `저장한 기록을 불러오면 경로와 점수, 세부 지표가 같다`() = runTest {
        val drawing = drawing(completedAtMillis = 1_000, score = 87)

        val id = repository.save(drawing).value()

        assertEquals(drawing.copy(id = id), repository.find(id).value())
    }

    @Test
    fun `저장할 때마다 새 ID를 받는다`() = runTest {
        val first = repository.save(drawing(completedAtMillis = 1, score = 10)).value()
        val second = repository.save(drawing(completedAtMillis = 2, score = 20)).value()

        assertTrue(first != second)
        assertEquals(10, repository.find(first).value()?.score)
        assertEquals(20, repository.find(second).value()?.score)
    }

    @Test
    fun `목록은 완료 일시 최신순 요약이다`() = runTest {
        repository.save(drawing(completedAtMillis = 1_000, score = 70))
        repository.save(drawing(completedAtMillis = 3_000, score = 90))
        repository.save(drawing(completedAtMillis = 2_000, score = 50))

        val summaries = repository.summaries().value()

        assertEquals(listOf(90, 50, 70), summaries.map { it.score })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), summaries.map { it.completedAtMillis })
        val summary = summaries.first()
        assertEquals(DrawingMode.CIRCLE, summary.mode)
        assertEquals(123.5, summary.distanceMeters, 0.0)
        assertEquals(60_000L, summary.durationMillis)
    }

    @Test
    fun `삭제한 기록은 조회되지 않고 경로와 세부 지표도 함께 지운다`() = runTest {
        val kept = repository.save(drawing(completedAtMillis = 1, score = 10)).value()
        val deleted = repository.save(drawing(completedAtMillis = 2, score = 20)).value()

        repository.delete(deleted).value()

        assertNull(repository.find(deleted).value())
        assertEquals(listOf(kept), repository.summaries().value().map { it.id })
        assertEquals(listOf(kept), childDrawingIds("completed_route_point"))
        assertEquals(listOf(kept), childDrawingIds("completed_metric"))
    }

    @Test
    fun `완료 시각과 모드가 같은 기록의 ID를 찾는다`() = runTest {
        repository.save(drawing(completedAtMillis = 1_000, score = 10)).value()
        val id = repository.save(drawing(completedAtMillis = 2_000, score = 20)).value()

        assertEquals(id, repository.findId(DrawingMode.CIRCLE, 2_000).value())
        assertNull(repository.findId(DrawingMode.CIRCLE, 3_000).value())
    }

    @Test
    fun `없는 기록을 삭제해도 성공한다`() = runTest {
        assertEquals(StorageResult.Success(Unit), repository.delete(42))
    }

    @Test
    fun `최고 점수를 조회한다`() = runTest {
        repository.save(drawing(completedAtMillis = 1, score = 70))
        repository.save(drawing(completedAtMillis = 2, score = 95))
        repository.save(drawing(completedAtMillis = 3, score = 80))

        assertEquals(95, repository.bestScore(DrawingMode.CIRCLE).value())
    }

    @Test
    fun `빈 저장소는 빈 목록과 최고 점수 없음을 돌려준다`() = runTest {
        assertEquals(emptyList<Any>(), repository.summaries().value())
        assertNull(repository.bestScore(DrawingMode.CIRCLE).value())
        assertNull(repository.find(1).value())
    }

    @Test
    fun `경로와 세부 지표가 없는 기록도 저장한다`() = runTest {
        val drawing = drawing(completedAtMillis = 1, score = 0)
            .copy(path = emptyList(), metrics = emptyMap())

        val id = repository.save(drawing).value()

        assertEquals(drawing.copy(id = id), repository.find(id).value())
    }

    @Test
    fun `저장된 값을 읽지 못하면 실패 결과를 돌려준다`() = runTest {
        database.completedDrawingDao().insertDrawing(
            CompletedDrawingEntity(
                mode = "unknown",
                completedAtMillis = 1,
                score = 1,
                distanceMeters = 0.0,
                durationMillis = 0
            )
        )

        assertTrue(repository.summaries() is StorageResult.Failure)
        assertTrue(repository.find(1) is StorageResult.Failure)
    }

    private fun childDrawingIds(table: String): List<Long> = database.openHelper.readableDatabase
        .query("SELECT DISTINCT drawing_id FROM $table ORDER BY drawing_id")
        .use { cursor -> List(cursor.count) { cursor.moveToNext().let { cursor.getLong(0) } } }

    private fun drawing(completedAtMillis: Long, score: Int) = CompletedDrawing(
        mode = DrawingMode.CIRCLE,
        completedAtMillis = completedAtMillis,
        path = listOf(
            RoutePoint(37.5665, 126.978, 100),
            RoutePoint(37.5666, 126.9781, 1_100),
            RoutePoint(37.5667, 126.978, 2_100)
        ),
        score = score,
        metrics = mapOf("circularity" to 0.92, "closure" to 0.8),
        distanceMeters = 123.5,
        durationMillis = 60_000
    )
}

internal fun <T> StorageResult<T>.value(): T = when (this) {
    is StorageResult.Success -> value
    is StorageResult.Failure -> throw AssertionError("저장소 작업이 실패했습니다.", cause)
}
