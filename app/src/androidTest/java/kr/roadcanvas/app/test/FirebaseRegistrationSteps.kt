package kr.roadcanvas.app.test

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.account.AnonymousAccount
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.upload.DrawingRegistration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class FirebaseRegistrationSteps {
    private val repository = FakeCompletedDrawingRepository()
    private var failOnce = false
    private val uploaded = mutableListOf<Long>()
    private val registration = DrawingRegistration(repository) { _, drawing ->
        if (failOnce) {
            failOnce = false
            error("offline")
        }
        uploaded.add(drawing.id)
    }

    @Given("Firebase에 등록할 기기 기록이 있다")
    fun localDrawing() = runBlocking {
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
        Unit
    }

    @Given("Firebase 등록이 한 번 실패한다")
    fun failure() {
        localDrawing()
        failOnce = true
    }

    @When("기록 등록을 실행한다")
    @When("기록 등록을 다시 실행한다")
    fun register() = runBlocking {
        try {
            registration.register(AnonymousAccount("user", "드라이버-user"))
        } catch (_: IllegalStateException) { }
    }

    @Then("기기 기록이 Firebase에 등록된다")
    fun uploaded() {
        assertEquals(listOf(1L), uploaded)
    }

    @Then("등록에 실패해도 기기 기록이 남는다")
    fun retained() {
        val saved = runBlocking { repository.find(1) } as StorageResult.Success
        assertNotNull(saved.value)
    }
}
