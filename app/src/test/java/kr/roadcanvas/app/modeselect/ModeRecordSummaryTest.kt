package kr.roadcanvas.app.modeselect

import java.io.IOException
import kotlinx.coroutines.test.runTest
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.fake.FakeTerritoryCaptureRepository
import kr.roadcanvas.app.territory.fake.FakeTerritoryOwnerProvider
import kr.roadcanvas.app.territory.fake.FakeTerritoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeRecordSummaryTest {
    private val repository = FakeCompletedDrawingRepository()
    private val store = FakeTerritoryStore()
    private val owner = FakeTerritoryOwnerProvider()
    private var saved = 0
    private val loader = ModeRecordSummaryLoader(
        repository,
        TerritoryGame(store, FakeTerritoryCaptureRepository(), owner)
    )

    @Test
    fun `기록이 없으면 세 모드 모두 아직 기록이 없다고 보인다`() {
        val summary = ModeRecordSummary()
        DrawingMode.entries.forEach { assertEquals(NO_RECORD_SUMMARY, summary.textFor(it)) }
    }

    @Test
    fun `동그라미 최고 점수, 자유그림 수와 내 땅 칸 수를 보인다`() {
        val summary = ModeRecordSummary(
            circleBestScore = 98,
            freeDrawingCount = 3,
            territoryCount = 1,
            territoryCells = 12
        )
        assertEquals("최고 98점", summary.textFor(DrawingMode.CIRCLE))
        assertEquals("내 그림 3개", summary.textFor(DrawingMode.FREE_DRAWING))
        assertEquals("내 땅 12칸", summary.textFor(DrawingMode.TERRITORY))
    }

    @Test
    fun `땅따먹기 기록이 있으면 칸을 모두 잃어도 0칸으로 보인다`() {
        val summary = ModeRecordSummary(territoryCount = 2, territoryCells = 0)
        assertEquals("내 땅 0칸", summary.textFor(DrawingMode.TERRITORY))
    }

    @Test
    fun `서버 칸 수를 읽지 못하면 땅따먹기 기기 기록 수를 보인다`() {
        val summary = ModeRecordSummary(territoryCount = 2, territoryCells = null)
        assertEquals("기록 2개", summary.textFor(DrawingMode.TERRITORY))
    }

    @Test
    fun `기기 기록 없이 서버 칸이 0이면 아직 기록이 없다고 보인다`() {
        val summary = ModeRecordSummary(territoryCells = 0)
        assertEquals(NO_RECORD_SUMMARY, summary.textFor(DrawingMode.TERRITORY))
    }

    @Test
    fun `기기 기록에서 동그라미 최고 점수와 모드별 기록 수를 읽는다`() = runTest {
        save(DrawingMode.CIRCLE, score = 72)
        save(DrawingMode.CIRCLE, score = 98)
        save(DrawingMode.FREE_DRAWING)
        save(DrawingMode.TERRITORY)
        save(DrawingMode.TERRITORY)

        assertEquals(
            ModeRecordSummary(circleBestScore = 98, freeDrawingCount = 1, territoryCount = 2),
            loader.loadLocal()
        )
    }

    @Test
    fun `기기 기록을 읽지 못하면 기록이 없는 요약을 준다`() = runTest {
        save(DrawingMode.CIRCLE, score = 98)
        repository.failure = IOException("읽기 실패")

        assertEquals(ModeRecordSummary(), loader.loadLocal())
    }

    @Test
    fun `서버에서 내 칸 수를 읽고 연결할 수 없으면 null을 준다`() = runTest {
        store.putCells(FakeTerritoryOwnerProvider.DEFAULT_OWNER.uid, "나", listOf("a", "b"))
        assertEquals(2L, loader.loadTerritoryCells())

        store.networkFailure = true
        assertNull(loader.loadTerritoryCells())
    }

    private suspend fun save(mode: DrawingMode, score: Int = 0) {
        repository.save(
            CompletedDrawing(
                mode = mode,
                completedAtMillis = 1_000L * ++saved,
                path = emptyList(),
                score = score,
                metrics = emptyMap(),
                distanceMeters = 0.0,
                durationMillis = 0
            )
        )
    }
}
