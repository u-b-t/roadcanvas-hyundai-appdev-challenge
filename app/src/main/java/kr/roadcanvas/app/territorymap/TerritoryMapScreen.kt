package kr.roadcanvas.app.territorymap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.challenge.Measurement
import kr.roadcanvas.app.challenge.formatArea
import kr.roadcanvas.app.map.CurrentPosition
import kr.roadcanvas.app.map.MapBounds
import kr.roadcanvas.app.map.MapCell
import kr.roadcanvas.app.map.MapExplore
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.territory.TerritoryMapCells
import kr.roadcanvas.app.territory.TerritoryRank
import kr.roadcanvas.app.territory.formatCells
import kr.roadcanvas.app.territory.territoryAreaOf
import kr.roadcanvas.app.territory.text
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.NavigationIcon
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.CONTROL_SPACING

const val TERRITORY_MAP_LABEL = "영토 지도"
const val TERRITORY_MAP_TITLE = "내 영토"
const val MY_CELLS_LABEL = "보유 칸"
const val MY_AREA_LABEL = "면적"
const val MY_LAND_LABEL = "내 땅으로"
const val TERRITORY_LOADING = "불러오는 중"
const val TERRITORY_EMPTY = "아직 차지한 칸이 없어요"
const val TERRITORY_EMPTY_BODY = "땅따먹기로 한 바퀴 돌아 둘러싼 땅을 차지해 보세요"
const val TERRITORY_TOO_MANY = "이 범위에는 칸이 너무 많아요. 지도를 확대해 주세요"
const val TERRITORY_RETRY_HINT = "홈으로 돌아간 뒤 다시 열어 주세요"
const val RANKING_TITLE = "순위"
const val RANKING_EMPTY = "아직 칸을 차지한 사용자가 없어요"
const val MY_RANK_LABEL = "나"
const val TERRITORY_MAP_OVERLAY_TAG = "territoryMapOverlay"
const val RANKING_ROW_TAG = "rankingRow"
const val MY_RANKING_ROW_TAG = "myRankingRow"

/** 영토 지도 화면에 서버 땅따먹기를 연결한다. [initialCenter]가 있으면 그 위치에서 지도를 연다. */
@Composable
fun TerritoryMapRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialCenter: RoutePoint? = null
) {
    val context = LocalContext.current
    val viewModel: TerritoryMapViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = context.appContainer
                TerritoryMapViewModel(
                    container.territoryGame,
                    container.completedDrawingRepository,
                    container.locationProvider,
                    initialCenter
                )
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    TerritoryMapScreen(
        state = state,
        onVisibleBoundsChange = viewModel::onVisibleBoundsChange,
        onBack = onBack,
        modifier = modifier,
        myLocation = myLocation,
        onMoveToMyLand = viewModel::moveToMyLand
    )
}

/**
 * 지도에 화면 범위의 칸을 내 칸과 다른 사용자 칸으로 나눠 채우고 [myLocation]이 있으면 내 위치를 표시한다.
 * 왼쪽 오버레이에 내 보유 칸·면적, 안내와 상위 순위를 둔다.
 * 내 땅으로, 홈으로 버튼은 오버레이 아래에 항상 보이게 둔다. 내 땅으로는 내 땅이 모인 곳을 알 때만 누를 수 있다.
 */
@Composable
fun TerritoryMapScreen(
    state: TerritoryMapUiState,
    onVisibleBoundsChange: (MapBounds) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    myLocation: RoutePoint? = null,
    onMoveToMyLand: () -> Unit = {}
) {
    val loadedCells = ((state.cells as? TerritoryLoad.Loaded)?.value as? TerritoryMapCells.Loaded)
        ?.cells
    val mapCells = remember(loadedCells) {
        loadedCells.orEmpty().map { cell ->
            MapCell(cell.bounds.corners.map { RoutePoint(it.latitude, it.longitude) }, cell.mine)
        }
    }
    DriverSideOverlay(
        background = {
            val center = state.center
            if (center == null) {
                Box(Modifier.fillMaxSize().background(SDGColor.Neutral100))
            } else {
                RouteMap(
                    points = emptyList(),
                    options = RouteMapOptions(
                        contentPadding = driverSideOverlayContentPadding(),
                        cells = mapCells,
                        explore = MapExplore(center, moveId = state.cameraMoveId),
                        currentPosition = myLocation?.let { CurrentPosition(it, null) },
                        onVisibleBoundsChange = onVisibleBoundsChange
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        controls = {
            // 순위가 길어 화면 높이를 넘으면 카드만 스크롤하고 버튼은 항상 보이게 둔다.
            OverlayCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag(TERRITORY_MAP_OVERLAY_TAG)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = TERRITORY_MAP_TITLE, style = MaterialTheme.typography.headlineSmall)
                    MyTerritory(state)
                    Notices(state)
                    Ranking(state.ranking)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)) {
                CarButton(
                    text = MY_LAND_LABEL,
                    onClick = onMoveToMyLand,
                    enabled = state.myLandCenter != null,
                    style = CarButtonStyle.Neutral,
                    modifier = Modifier.weight(1f)
                )
            }
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                icon = NavigationIcon.Home,
                onClick = onBack,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier
    )
}

@Composable
private fun MyTerritory(state: TerritoryMapUiState) {
    val count = (state.myCellCount as? TerritoryLoad.Loaded)?.value
    val placeholder = if (state.myCellCount is TerritoryLoad.Loading) TERRITORY_LOADING else "-"
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Measurement(MY_CELLS_LABEL, count?.let(::formatCells) ?: placeholder)
        Measurement(MY_AREA_LABEL, count?.let { formatArea(territoryAreaOf(it)) } ?: placeholder)
    }
}

/** 실패, 빈 영토, 너무 많은 칸 안내. 실패는 이유 하나만 보여준다. */
@Composable
private fun Notices(state: TerritoryMapUiState) {
    state.failure?.let {
        Text(
            text = it.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Message(TERRITORY_RETRY_HINT)
    }
    if (state.emptyTerritory) {
        Text(text = TERRITORY_EMPTY, style = MaterialTheme.typography.titleMedium)
        Message(TERRITORY_EMPTY_BODY)
    }
    if (state.tooManyCells) {
        Text(
            text = TERRITORY_TOO_MANY,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun Ranking(ranking: TerritoryLoad<List<TerritoryRank>>) {
    Text(text = RANKING_TITLE, style = MaterialTheme.typography.titleLarge)
    when (ranking) {
        TerritoryLoad.Loading -> Message(TERRITORY_LOADING)
        is TerritoryLoad.Failed -> Message("-")
        is TerritoryLoad.Loaded -> if (ranking.value.isEmpty()) {
            Message(RANKING_EMPTY)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ranking.value.forEach { RankRow(it) }
            }
        }
    }
}

/** 순위 한 줄. 내 순위는 강조 배경과 "나" 표시로 구분한다. */
@Composable
private fun RankRow(rank: TerritoryRank) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SDGCornerRadius.BoxRadius.Radius12)
            .background(if (rank.mine) SDGColor.Primary50 else SDGColor.Neutral0)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(if (rank.mine) MY_RANKING_ROW_TAG else RANKING_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (rank.mine) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = "${rank.rank}",
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier.width(40.dp)
        )
        Text(
            text = rank.nickname,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (rank.mine) {
            Text(
                text = MY_RANK_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(SDGCornerRadius.BoxRadius.Radius8)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(text = formatCells(rank.cellCount), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
