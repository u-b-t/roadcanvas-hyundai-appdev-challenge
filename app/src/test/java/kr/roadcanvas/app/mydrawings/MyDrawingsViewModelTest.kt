package kr.roadcanvas.app.mydrawings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kr.roadcanvas.app.drawing.CompletedDrawing
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.drawing.fake.FakeCompletedDrawingRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyDrawingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val fake = FakeCompletedDrawingRepository()
    private val repository = GatedRepository(fake)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun save(minute: Int, score: Int): Long {
        val drawing = CompletedDrawing(
            mode = DrawingMode.CIRCLE,
            completedAtMillis = minute * 60_000L,
            path = listOf(RoutePoint(37.5, 127.0, 0L), RoutePoint(37.6, 127.0, 1_000L)),
            score = score,
            metrics = emptyMap(),
            distanceMeters = 100.0,
            durationMillis = 60_000
        )
        return (fake.save(drawing) as StorageResult.Success).value
    }

    private val MyDrawingsUiState.items: List<CompletedDrawingSummary>
        get() = (list as DrawingListState.Loaded).items

    private val MyDrawingsUiState.loadedDrawing: CompletedDrawing
        get() = (detail as DrawingDetailState.Loaded).drawing

    @Test
    fun `목록을 불러오는 동안 로딩 상태다`() = runTest(dispatcher) {
        repository.summariesGate = CompletableDeferred()
        val viewModel = MyDrawingsViewModel(repository)

        assertEquals(DrawingListState.Loading, viewModel.state.value.list)

        repository.summariesGate?.complete(Unit)
        assertEquals(DrawingListState.Empty, viewModel.state.value.list)
    }

    @Test
    fun `기록이 없으면 빈 상태이고 선택한 기록이 없다`() = runTest(dispatcher) {
        val state = MyDrawingsViewModel(repository).state.value

        assertEquals(DrawingListState.Empty, state.list)
        assertNull(state.selectedId)
        assertNull(state.detail)
    }

    @Test
    fun `목록은 최신순이고 가장 최근 기록의 상세를 불러온다`() = runTest(dispatcher) {
        save(minute = 1, score = 70)
        val latest = save(minute = 3, score = 50)
        save(minute = 2, score = 90)

        val state = MyDrawingsViewModel(repository).state.value

        assertEquals(listOf(50, 90, 70), state.items.map { it.score })
        assertEquals(90, (state.list as DrawingListState.Loaded).best?.score)
        assertEquals(latest, state.selectedId)
        assertEquals(latest, state.loadedDrawing.id)
    }

    @Test
    fun `목록 조회에 실패하면 오류 상태이고 다시 시도하면 목록을 불러온다`() = runTest(dispatcher) {
        save(minute = 1, score = 70)
        fake.failure = IllegalStateException("저장소 오류")
        val viewModel = MyDrawingsViewModel(repository)

        assertEquals(DrawingListState.Error, viewModel.state.value.list)

        fake.failure = null
        viewModel.retry()

        assertEquals(listOf(70), viewModel.state.value.items.map { it.score })
    }

    @Test
    fun `기록을 선택하면 해당 기록의 상세를 불러온다`() = runTest(dispatcher) {
        val older = save(minute = 1, score = 70)
        save(minute = 2, score = 50)
        val viewModel = MyDrawingsViewModel(repository)

        viewModel.select(older)

        assertEquals(older, viewModel.state.value.selectedId)
        assertEquals(70, viewModel.state.value.loadedDrawing.score)
    }

    @Test
    fun `상세를 불러오는 동안 로딩 상태이고 실패하면 오류 상태다`() = runTest(dispatcher) {
        val id = save(minute = 1, score = 70)
        val viewModel = MyDrawingsViewModel(repository)
        repository.findGate = CompletableDeferred()

        viewModel.select(id)
        assertEquals(DrawingDetailState.Loading, viewModel.state.value.detail)

        fake.failure = IllegalStateException("저장소 오류")
        repository.findGate?.complete(Unit)
        assertEquals(DrawingDetailState.Error, viewModel.state.value.detail)
    }

    @Test
    fun `목록을 불러온 뒤 기록이 사라졌으면 찾을 수 없음 상태다`() = runTest(dispatcher) {
        val id = save(minute = 1, score = 70)
        val viewModel = MyDrawingsViewModel(repository)
        fake.delete(id)

        viewModel.select(id)

        assertEquals(DrawingDetailState.NotFound, viewModel.state.value.detail)
    }

    @Test
    fun `삭제는 확인한 뒤에만 진행하고 취소하면 기록을 유지한다`() = runTest(dispatcher) {
        save(minute = 1, score = 70)
        val viewModel = MyDrawingsViewModel(repository)

        viewModel.requestDelete()
        assertTrue(viewModel.state.value.deleteConfirmVisible)

        viewModel.cancelDelete()

        assertFalse(viewModel.state.value.deleteConfirmVisible)
        assertEquals(1, viewModel.state.value.items.size)
        assertEquals(1, (fake.summaries() as StorageResult.Success).value.size)
    }

    @Test
    fun `삭제 중에는 삭제 중 상태이고 성공하면 목록에서 빼고 다음 기록을 선택한다`() = runTest(dispatcher) {
        val oldest = save(minute = 1, score = 70)
        val middle = save(minute = 2, score = 90)
        save(minute = 3, score = 50)
        val viewModel = MyDrawingsViewModel(repository)
        viewModel.select(middle)
        repository.deleteGate = CompletableDeferred()

        viewModel.requestDelete()
        viewModel.confirmDelete()
        assertTrue(viewModel.state.value.deleting)
        assertFalse(viewModel.state.value.deleteConfirmVisible)

        repository.deleteGate?.complete(Unit)

        val state = viewModel.state.value
        assertFalse(state.deleting)
        assertEquals(listOf(50, 70), state.items.map { it.score })
        assertEquals(70, (state.list as DrawingListState.Loaded).best?.score)
        assertEquals(oldest, state.selectedId)
        assertEquals(oldest, state.loadedDrawing.id)
    }

    @Test
    fun `삭제 중에는 다른 기록을 선택하지 않는다`() = runTest(dispatcher) {
        val older = save(minute = 1, score = 70)
        val latest = save(minute = 2, score = 50)
        val viewModel = MyDrawingsViewModel(repository)
        repository.deleteGate = CompletableDeferred()
        viewModel.requestDelete()
        viewModel.confirmDelete()

        viewModel.select(older)

        assertEquals(latest, viewModel.state.value.selectedId)
    }

    @Test
    fun `마지막 기록을 삭제하면 빈 상태가 된다`() = runTest(dispatcher) {
        save(minute = 1, score = 70)
        val viewModel = MyDrawingsViewModel(repository)

        viewModel.requestDelete()
        viewModel.confirmDelete()

        assertEquals(DrawingListState.Empty, viewModel.state.value.list)
        assertNull(viewModel.state.value.detail)
    }

    @Test
    fun `삭제에 실패하면 실패를 알리고 상세와 목록을 유지한다`() = runTest(dispatcher) {
        val id = save(minute = 1, score = 70)
        val viewModel = MyDrawingsViewModel(repository)
        fake.failure = IllegalStateException("저장소 오류")

        viewModel.requestDelete()
        viewModel.confirmDelete()

        val state = viewModel.state.value
        assertTrue(state.deleteFailed)
        assertFalse(state.deleting)
        assertEquals(id, state.loadedDrawing.id)
        assertEquals(listOf(id), state.items.map { it.id })
    }

    @Test
    fun `다른 기록을 선택하면 삭제 실패 안내를 지운다`() = runTest(dispatcher) {
        val older = save(minute = 1, score = 70)
        save(minute = 2, score = 50)
        val viewModel = MyDrawingsViewModel(repository)
        fake.failure = IllegalStateException("저장소 오류")
        viewModel.requestDelete()
        viewModel.confirmDelete()
        fake.failure = null

        viewModel.select(older)

        assertFalse(viewModel.state.value.deleteFailed)
    }

    /** 원하는 시점까지 응답을 미뤄 로딩과 삭제 중 상태를 확인하는 저장소 */
    private class GatedRepository(private val delegate: CompletedDrawingRepository) :
        CompletedDrawingRepository by delegate {
        var summariesGate: CompletableDeferred<Unit>? = null
        var findGate: CompletableDeferred<Unit>? = null
        var deleteGate: CompletableDeferred<Unit>? = null

        override suspend fun summaries(): StorageResult<List<CompletedDrawingSummary>> {
            summariesGate?.await()
            return delegate.summaries()
        }

        override suspend fun find(id: Long): StorageResult<CompletedDrawing?> {
            findGate?.await()
            return delegate.find(id)
        }

        override suspend fun delete(id: Long): StorageResult<Unit> {
            deleteGate?.await()
            return delegate.delete(id)
        }
    }
}
