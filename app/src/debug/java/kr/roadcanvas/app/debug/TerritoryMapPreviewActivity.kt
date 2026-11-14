package kr.roadcanvas.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.territory.StoredTerritoryCapture
import kr.roadcanvas.app.territory.StoredTerritoryCell
import kr.roadcanvas.app.territory.StoredTerritoryOwner
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureRepository
import kr.roadcanvas.app.territory.TerritoryCaptureRequest
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryGrid
import kr.roadcanvas.app.territory.TerritoryGridCell
import kr.roadcanvas.app.territory.TerritoryOwner
import kr.roadcanvas.app.territory.TerritoryOwnerProvider
import kr.roadcanvas.app.territory.TerritoryStore
import kr.roadcanvas.app.territorymap.DEFAULT_TERRITORY_MAP_CENTER
import kr.roadcanvas.app.territorymap.TerritoryMapScreen
import kr.roadcanvas.app.territorymap.TerritoryMapViewModel
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme

private val PREVIEW_OWNER = TerritoryOwner("preview", "드라이버-preview")

/**
 * 영토 지도와 순위를 서버 없이 확인하는 디버그 전용 화면. 서울시청 주변에 내 칸과 다른 사용자 칸을 메모리에 둔다.
 * `adb shell am start -n kr.roadcanvas.app/.debug.TerritoryMapPreviewActivity`로 연다.
 * 지도는 앱과 같은 네이버 지도 컴포넌트를 쓰므로 지도 키가 있어야 칸이 지도 위에 보인다.
 */
class TerritoryMapPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = TerritoryGame(
            SampleTerritoryStore(),
            EmptyCaptureRepository,
            TerritoryOwnerProvider { PREVIEW_OWNER }
        )
        val repository = appContainer.completedDrawingRepository
        val locationProvider = appContainer.locationProvider
        setContent {
            RoadCanvasTheme {
                val viewModel: TerritoryMapViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            TerritoryMapViewModel(
                                game,
                                repository,
                                locationProvider,
                                DEFAULT_TERRITORY_MAP_CENTER
                            )
                        }
                    }
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
                TerritoryMapScreen(
                    state = state,
                    onVisibleBoundsChange = viewModel::onVisibleBoundsChange,
                    onBack = ::finish,
                    myLocation = myLocation,
                    onMoveToMyLand = viewModel::moveToMyLand
                )
            }
        }
    }
}

/** 미리보기는 획득을 만들지 않는다. */
private object EmptyCaptureRepository : TerritoryCaptureRepository {
    override suspend fun findByDrawing(drawingId: Long) = StorageResult.Success(null)

    override suspend fun pending() = StorageResult.Success(emptyList<StoredTerritoryCapture>())

    override suspend fun save(capture: StoredTerritoryCapture): StorageResult<Unit> =
        StorageResult.Success(Unit)
}

/** 서울시청 주변 블록 몇 개를 사용자별로 나눠 가진 서버 저장소 */
private class SampleTerritoryStore : TerritoryStore {
    private val nicknames = mapOf(
        PREVIEW_OWNER.uid to PREVIEW_OWNER.nickname,
        "a" to "드라이버-3f9a1c20",
        "b" to "드라이버-8b2d77e1",
        "c" to "드라이버-c41e0a9b"
    )
    private val cells: Map<String, String> = buildMap {
        block(PREVIEW_OWNER.uid, rows = -1..2, cols = -2..1)
        block("a", rows = -1..1, cols = 3..6)
        block("b", rows = 3..5, cols = -3..0)
        block("c", rows = -4..-3, cols = 0..4)
    }

    override suspend fun submit(owner: TerritoryOwner, request: TerritoryCaptureRequest) =
        TerritoryCaptureOutcome(newCells = request.cells.size, stolenCells = 0, ownCells = 0)

    override suspend fun cells(regions: List<String>, limit: Int) = cells
        .filterKeys { TerritoryGrid.regionOf(it) in regions }
        .map { (id, owner) -> StoredTerritoryCell(id, owner) }
        .take(limit)

    override suspend fun ownedCellCount(uid: String) = cells.values.count { it == uid }.toLong()

    override suspend fun topOwners(limit: Int) = cells.values.groupingBy { it }.eachCount()
        .map { (uid, count) -> StoredTerritoryOwner(uid, nicknames.getValue(uid), count.toLong()) }
        .sortedByDescending { it.cellCount }
        .take(limit)

    private fun MutableMap<String, String>.block(owner: String, rows: IntRange, cols: IntRange) {
        val center = DEFAULT_TERRITORY_MAP_CENTER
        val base = TerritoryGrid.cellOf(GeoPoint(center.latitude, center.longitude))
        for (row in rows) {
            for (col in cols) {
                put(TerritoryGridCell(base.row + row, base.col + col).id, owner)
            }
        }
    }
}
