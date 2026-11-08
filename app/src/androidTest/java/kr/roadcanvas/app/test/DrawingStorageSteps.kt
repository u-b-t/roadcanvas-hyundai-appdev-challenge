package kr.roadcanvas.app.test

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kr.roadcanvas.app.RoadCanvasApplication
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.DrawingStorage
import kr.roadcanvas.app.drawing.InProgressDrawing
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.room.DrawingDatabase
import kr.roadcanvas.app.drawing.room.toStorage
import kr.roadcanvas.app.vehicle.fake.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** 기기 저장소 시나리오. Room 저장소는 테스트 전용 DB 파일을 직접 호출해 검증한다. */
class DrawingStorageSteps {
    private val application = ApplicationProvider.getApplicationContext<RoadCanvasApplication>()
    private val fakeContainer get() = application.container as FakeAppContainer

    private var database: DrawingDatabase? = null
    private lateinit var storage: DrawingStorage
    private var savedDrawing: CompletedDrawing? = null
    private var summaries = emptyList<CompletedDrawingSummary>()
    private var loadedInProgress: InProgressDrawing? = null
    private var lastResult: StorageResult<*>? = null

    @After
    fun closeDatabase() {
        database?.close()
        application.deleteDatabase(TEST_DATABASE)
    }

    @Given("기기 저장소가 비어 있다")
    fun openEmptyStorage() {
        application.deleteDatabase(TEST_DATABASE)
        openStorage()
    }

    @Given("점수 {int}점, 경로 점 {int}개인 동그라미 그림을 저장한다")
    fun saveDrawing(score: Int, pointCount: Int) {
        val drawing = drawing(completedAtMillis = 60_000, score = score, pointCount = pointCount)
        val id = runBlocking { storage.completed.save(drawing) }.value()
        savedDrawing = drawing.copy(id = id)
    }

    @Given("완료 시각 {int}분에 점수 {int}점인 동그라미 그림을 저장했다")
    fun saveDrawingAt(minute: Int, score: Int) {
        runBlocking {
            storage.completed.save(drawing(minute * MINUTE, score, pointCount = 3))
        }.value()
    }

    @Then("저장한 그림을 불러오면 경로와 점수, 세부 지표가 저장한 내용과 같다")
    fun assertSavedDrawingLoaded() {
        val saved = checkNotNull(savedDrawing)
        assertEquals(saved, runBlocking { storage.completed.find(saved.id) }.value())
    }

    @When("완료 기록 목록을 조회한다")
    fun loadSummaries() {
        summaries = runBlocking { storage.completed.summaries() }.value()
    }

    @Then("완료 기록 목록의 점수는 {string} 순서다")
    fun assertSummaryOrder(scores: String) {
        assertEquals(scores, summaries.joinToString { it.score.toString() })
    }

    @Then("동그라미 최고 점수는 {int}점이다")
    fun assertBestScore(score: Int) {
        assertEquals(score, runBlocking { storage.completed.bestScore(DrawingMode.CIRCLE) }.value())
    }

    @Then("동그라미 최고 점수가 없다")
    fun assertNoBestScore() {
        assertNull(runBlocking { storage.completed.bestScore(DrawingMode.CIRCLE) }.value())
    }

    @When("저장한 그림을 삭제한다")
    fun deleteSavedDrawing() {
        runBlocking { storage.completed.delete(checkNotNull(savedDrawing).id) }.value()
    }

    @Then("저장한 그림을 불러올 수 없다")
    fun assertSavedDrawingMissing() {
        assertNull(runBlocking { storage.completed.find(checkNotNull(savedDrawing).id) }.value())
    }

    @Then("완료 기록 목록이 비어 있다")
    fun assertNoSummaries() {
        assertEquals(emptyList<Any>(), runBlocking { storage.completed.summaries() }.value())
    }

    @Given("시작 시각 {int}분에 동그라미 도전을 시작했다")
    fun startDrawing(minute: Int) {
        runBlocking { storage.inProgress.start(DrawingMode.CIRCLE, minute * MINUTE) }.value()
    }

    @Given("진행 중인 도전에 경로 점 {int}개를 하나씩 추가했다")
    fun appendPointsOneByOne(count: Int) {
        routePoints(count).forEach { point ->
            runBlocking { storage.inProgress.appendPoints(listOf(point)) }.value()
        }
    }

    @When("저장소를 닫았다가 다시 열어 진행 중인 도전을 불러온다")
    fun reopenAndLoadInProgress() {
        database?.close()
        openStorage()
        loadedInProgress = runBlocking { storage.inProgress.load() }.value()
    }

    @Then("진행 중인 도전의 시작 시각은 {int}분이고 경로 점 {int}개가 추가한 순서대로 있다")
    fun assertInProgressRestored(minute: Int, count: Int) {
        assertEquals(
            InProgressDrawing(DrawingMode.CIRCLE, minute * MINUTE, routePoints(count)),
            loadedInProgress
        )
    }

    @When("진행 중인 도전을 포기해 삭제한다")
    fun abandonInProgress() {
        runBlocking { storage.inProgress.clear() }.value()
    }

    @Then("진행 중인 도전이 없다")
    fun assertNoInProgress() {
        assertNull(runBlocking { storage.inProgress.load() }.value())
    }

    @When("진행 중인 도전에 경로 점 {int}개를 추가한다")
    fun appendPoints(count: Int) {
        lastResult = runBlocking { storage.inProgress.appendPoints(routePoints(count)) }
    }

    @Then("경로 점 추가는 실패 결과를 받는다")
    fun assertAppendFailed() {
        assertTrue("결과: $lastResult", lastResult is StorageResult.Failure)
    }

    @Given("테스트용 저장소가 실패하도록 설정되어 있다")
    fun injectFailure() {
        val error = IllegalStateException("테스트 저장소 오류")
        fakeContainer.completedDrawingRepository.failure = error
        fakeContainer.inProgressDrawingRepository.failure = error
    }

    @When("테스트용 저장소에 {string}을 요청한다")
    fun requestFakeStorage(operation: String) {
        val completed = application.container.completedDrawingRepository
        val inProgress = application.container.inProgressDrawingRepository
        lastResult = runBlocking {
            when (operation) {
                "완료 기록 저장" -> completed.save(drawing(MINUTE, score = 1, pointCount = 1))
                "완료 기록 목록 조회" -> completed.summaries()
                "완료 기록 단건 조회" -> completed.find(1)
                "완료 기록 삭제" -> completed.delete(1)
                "최고 점수 조회" -> completed.bestScore(DrawingMode.CIRCLE)
                "진행 중 기록 시작" -> inProgress.start(DrawingMode.CIRCLE, MINUTE)
                "경로 점 추가" -> inProgress.appendPoints(routePoints(1))
                "진행 중 기록 불러오기" -> inProgress.load()
                "진행 중 기록 삭제" -> inProgress.clear()
                else -> throw IllegalArgumentException("알 수 없는 작업: $operation")
            }
        }
    }

    @Then("요청한 작업은 실패 결과를 받는다")
    fun assertRequestFailed() {
        val result = lastResult
        assertTrue("결과: $result", result is StorageResult.Failure)
        assertEquals("테스트 저장소 오류", (result as StorageResult.Failure).cause.message)
    }

    @Given("버전 {int} 스키마로 만든 저장소 DB가 있다")
    fun createDatabaseAtVersion(version: Int) {
        application.deleteDatabase(TEST_DATABASE)
        migrationHelper.createDatabase(TEST_DATABASE, version).close()
    }

    @When("등록된 마이그레이션으로 현재 버전까지 올린다")
    fun migrateToCurrentVersion() {
        migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            DrawingDatabase.VERSION,
            true,
            *DrawingDatabase.MIGRATIONS
        ).close()
    }

    @Then("현재 버전 스키마와 일치한다")
    fun assertCurrentSchema() {
        // runMigrationsAndValidate가 스키마가 다르면 실패한다. 여기서는 버전을 확인한다.
        openStorage()
        assertEquals(
            DrawingDatabase.VERSION,
            checkNotNull(database).openHelper.readableDatabase.version
        )
    }

    @Then("올린 DB에서 완료 기록 목록이 비어 있다")
    fun assertMigratedDatabaseEmpty() {
        assertNoSummaries()
    }

    private val migrationHelper by lazy {
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DrawingDatabase::class.java
        )
    }

    private fun openStorage() {
        val opened = DrawingDatabase.create(application, TEST_DATABASE)
        database = opened
        storage = opened.toStorage()
    }

    private fun drawing(completedAtMillis: Long, score: Int, pointCount: Int) = CompletedDrawing(
        mode = DrawingMode.CIRCLE,
        completedAtMillis = completedAtMillis,
        path = routePoints(pointCount),
        score = score,
        metrics = mapOf("circularity" to 0.92, "closure" to 0.81),
        distanceMeters = 314.2,
        durationMillis = 63_000
    )

    private fun routePoints(count: Int) = List(count) { index ->
        RoutePoint(37.5665 + index * 0.0001, 126.978, 1_000L * (index + 1))
    }

    private fun <T> StorageResult<T>.value(): T = when (this) {
        is StorageResult.Success -> value
        is StorageResult.Failure -> throw AssertionError("저장소 작업이 실패했습니다.", cause)
    }

    private companion object {
        const val TEST_DATABASE = "test-drawings.db"
        const val MINUTE = 60_000L
    }
}
