package kr.roadcanvas.app.territorymap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kr.roadcanvas.app.drawing.CompletedDrawingRepository
import kr.roadcanvas.app.drawing.CompletedDrawingSummary
import kr.roadcanvas.app.drawing.StorageResult
import kr.roadcanvas.app.map.MapBounds
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.territory.GeoBounds
import kr.roadcanvas.app.territory.TerritoryCall
import kr.roadcanvas.app.territory.TerritoryFailure
import kr.roadcanvas.app.territory.TerritoryGame
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.TerritoryRank
import kr.roadcanvas.app.vehicle.LocationProvider

/** 영토 지도를 처음 열 위치가 없을 때 쓰는 서울시청 */
val DEFAULT_TERRITORY_MAP_CENTER = RoutePoint(37.5665, 126.9780)

/** 영토 지도를 열 때 현재 위치를 기다리는 최대 시간 */
const val TERRITORY_MAP_LOCATION_WAIT_MILLIS = 2_000L

/** 서버에서 읽는 값 하나의 상태 */
sealed interface TerritoryLoad<out T> {
    data object Loading : TerritoryLoad<Nothing>

    data class Loaded<out T>(val value: T) : TerritoryLoad<T>

    data class Failed(val reason: TerritoryFailure) : TerritoryLoad<Nothing>
}

/**
 * 영토 지도 화면 상태. [center]는 지도 카메라를 옮길 위치이며 정하기 전이면 null이다.
 * 같은 위치로 다시 옮길 때도 [cameraMoveId]가 바뀐다. [myLandCenter]는 내 땅이 모인 곳이며 모르면 null이다.
 * [cells]는 지도 범위를 받기 전에는 불러오는 중이다.
 */
data class TerritoryMapUiState(
    val center: RoutePoint? = null,
    val cameraMoveId: Long = 0,
    val myLandCenter: RoutePoint? = null,
    val cells: TerritoryLoad<TerritoryMapCells> = TerritoryLoad.Loading,
    val myCellCount: TerritoryLoad<Long> = TerritoryLoad.Loading,
    val ranking: TerritoryLoad<List<TerritoryRank>> = TerritoryLoad.Loading
) {
    /** 화면에 한 번만 안내할 실패 이유. 내 칸, 순위, 지도 칸 순서로 처음 실패한 것이다. */
    val failure: TerritoryFailure?
        get() = listOf(myCellCount, ranking, cells)
            .firstNotNullOfOrNull { (it as? TerritoryLoad.Failed)?.reason }

    /** 내 칸이 하나도 없는지. 내 칸 수를 읽기 전이나 실패했으면 false다. */
    val emptyTerritory: Boolean
        get() = (myCellCount as? TerritoryLoad.Loaded)?.value == 0L

    /** 읽을 칸이 많아 지도에 그리지 않았다. */
    val tooManyCells: Boolean
        get() = (cells as? TerritoryLoad.Loaded)?.value is TerritoryMapCells.TooMany
}

/**
 * 영토 지도와 순위. 들어오면 반영하지 못한 획득을 먼저 다시 제출한 뒤 내 칸 수와 순위를 읽는다.
 * 지도 칸은 실시간으로 구독하지 않고 지도 이동이 끝날 때마다 화면 범위로 다시 읽는다. [refresh]는 모두 다시 읽는다.
 * 처음 열 위치는 [initialCenter], 없으면 [TERRITORY_MAP_LOCATION_WAIT_MILLIS] 안에 받은 현재 위치,
 * 받지 못하면 가장 최근 완료 기록의 마지막 위치, 그것도 없으면 [DEFAULT_TERRITORY_MAP_CENTER]다.
 * 내 위치 [myLocation]은 화면이 보는 동안만 받는다.
 */
class TerritoryMapViewModel(
    private val game: TerritoryGame,
    private val completedRepository: CompletedDrawingRepository,
    locationProvider: LocationProvider,
    private val initialCenter: RoutePoint? = null,
    private val locationWaitMillis: Long = TERRITORY_MAP_LOCATION_WAIT_MILLIS
) : ViewModel() {
    private val mutableState = MutableStateFlow(TerritoryMapUiState(center = initialCenter))
    val state: StateFlow<TerritoryMapUiState> = mutableState.asStateFlow()

    /** 내 위치. 아직 받지 못했으면 null이다. 화면을 떠나면 위치 수신을 바로 멈춘다. */
    val myLocation: StateFlow<RoutePoint?> = locationProvider.locations()
        .map { RoutePoint(it.latitude, it.longitude) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    private var bounds: MapBounds? = null
    private var summaryJob: Job? = null
    private var cellsJob: Job? = null

    init {
        viewModelScope.launch {
            val summaries = summaries()
            mutableState.update { it.copy(myLandCenter = myLandCenter(summaries)) }
            if (initialCenter == null) {
                val center = withTimeoutOrNull(locationWaitMillis) {
                    myLocation.filterNotNull().first()
                } ?: lastRecordedPoint(summaries) ?: DEFAULT_TERRITORY_MAP_CENTER
                // 기다리는 동안 내 땅으로 옮겼으면 그 위치를 그대로 둔다.
                mutableState.update { it.copy(center = it.center ?: center) }
            }
        }
        refresh()
    }

    /** 내 땅이 모인 곳으로 지도를 옮긴다. 모르면 아무것도 하지 않는다. */
    fun moveToMyLand() {
        mutableState.update { state ->
            val land = state.myLandCenter ?: return@update state
            state.copy(center = land, cameraMoveId = state.cameraMoveId + 1)
        }
    }

    /** 반영하지 못한 획득을 다시 제출하고 내 칸 수, 순위와 지금 지도 범위의 칸을 다시 읽는다. */
    fun refresh() {
        summaryJob?.cancel()
        mutableState.update {
            it.copy(myCellCount = TerritoryLoad.Loading, ranking = TerritoryLoad.Loading)
        }
        summaryJob = viewModelScope.launch {
            game.resubmitPending()
            val count = async { game.myCellCount().toLoad() }
            val ranking = async { game.ranking().toLoad() }
            mutableState.update { it.copy(myCellCount = count.await(), ranking = ranking.await()) }
            bounds?.let(::loadCells)
        }
    }

    /** 지도 이동이 끝났다. 그 범위의 칸을 다시 읽는다. */
    fun onVisibleBoundsChange(visible: MapBounds) {
        bounds = visible
        loadCells(visible)
    }

    private fun loadCells(visible: MapBounds) {
        cellsJob?.cancel()
        mutableState.update { it.copy(cells = TerritoryLoad.Loading) }
        cellsJob = viewModelScope.launch {
            val loaded = game.mapCells(
                GeoBounds(visible.south, visible.west, visible.north, visible.east)
            ).toLoad()
            if (bounds == visible) mutableState.update { it.copy(cells = loaded) }
        }
    }

    /** 완료 기록 요약. 읽지 못하면 기록이 없는 것처럼 기본 위치를 쓴다. */
    private suspend fun summaries(): List<CompletedDrawingSummary> =
        (completedRepository.summaries() as? StorageResult.Success)?.value.orEmpty()

    private fun <T> TerritoryCall<T>.toLoad(): TerritoryLoad<T> = when (this) {
        is TerritoryCall.Success -> TerritoryLoad.Loaded(value)
        is TerritoryCall.Failure -> TerritoryLoad.Failed(reason)
    }
}
