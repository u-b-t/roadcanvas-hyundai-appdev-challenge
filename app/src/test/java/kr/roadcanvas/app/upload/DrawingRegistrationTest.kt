package kr.roadcanvas.app.upload

import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DrawingRegistrationTest {
    @Test
    fun `등록 실패 뒤 기기 기록은 남고 다음 시도에서 다시 등록된다`() = runTest {
        val repository = FakeCompletedDrawingRepository()
        repository.save(
            CompletedDrawing(
                mode = DrawingMode.CIRCLE,
                completedAtMillis = 1000,
                path = emptyList(),
                score = 0,
                metrics = emptyMap(),
                distanceMeters = 0.0,
                durationMillis = 1000
            )
        )
        var attempts = 0
        val uploaded = mutableListOf<Long>()
        val registration = DrawingRegistration(repository) { _, drawing ->
            if (attempts++ == 0) error("offline")
            uploaded.add(drawing.id)
        }
        val account = AnonymousAccount("user", "드라이버-user")
        try {
            registration.register(account)
        } catch (_: IllegalStateException) { }
        assertNotNull((repository.find(1) as kr.roadcanvas.app.drawing.StorageResult.Success).value)
        registration.register(account)
        assertEquals(listOf(1L), uploaded)
    }
}
