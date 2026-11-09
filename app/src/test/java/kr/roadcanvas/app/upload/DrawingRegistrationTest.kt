package kr.roadcanvas.app.upload

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingRegistrationTest {
    private val account = AnonymousAccount("user", "드라이버-user")

    private fun drawing(completedAtMillis: Long) = CompletedDrawing(
        mode = DrawingMode.CIRCLE,
        completedAtMillis = completedAtMillis,
        path = emptyList(),
        score = 0,
        metrics = emptyMap(),
        distanceMeters = 0.0,
        durationMillis = 1000
    )

    @Test
    fun `등록 실패 뒤 기기 기록은 남고 다음 시도에서 다시 등록된다`() = runTest {
        val repository = FakeCompletedDrawingRepository()
        repository.save(drawing(1000))
        var attempts = 0
        val uploaded = mutableListOf<Long>()
        val registration = DrawingRegistration(repository) { _, drawing ->
            if (attempts++ == 0) error("offline")
            uploaded.add(drawing.id)
        }
        try {
            registration.register(account)
        } catch (_: IllegalStateException) { }
        assertNotNull((repository.find(1) as StorageResult.Success).value)
        registration.register(account)
        assertEquals(listOf(1L), uploaded)
    }

    @Test
    fun `등록한 기록은 다시 보내지 않는다`() = runTest {
        val repository = FakeCompletedDrawingRepository()
        repository.save(drawing(1000))
        val uploaded = mutableListOf<Long>()
        val registration =
            DrawingRegistration(repository) { _, drawing -> uploaded.add(drawing.id) }
        registration.register(account)
        registration.register(account)
        assertEquals(listOf(1L), uploaded)
    }

    @Test
    fun `한 기록이 실패해도 나머지 기록을 등록하고 오류를 알린다`() = runTest {
        val repository = FakeCompletedDrawingRepository()
        val failingId = (repository.save(drawing(1000)) as StorageResult.Success).value
        val otherId = (repository.save(drawing(2000)) as StorageResult.Success).value
        val uploaded = mutableListOf<Long>()
        val registration = DrawingRegistration(repository) { _, drawing ->
            if (drawing.id == failingId) error("offline")
            uploaded.add(drawing.id)
        }
        val error = runCatching { registration.register(account) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(listOf(otherId), uploaded)
    }

    @Test
    fun `기기 기록 목록을 읽지 못하면 오류를 알린다`() = runTest {
        val repository = FakeCompletedDrawingRepository().apply {
            failure =
                IllegalStateException("db")
        }
        val registration = DrawingRegistration(repository) { _, _ -> }
        val error = runCatching { registration.register(account) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }
}
