package kr.roadcanvas.app.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.map.MapCell
import kr.roadcanvas.app.map.MapCellStyle
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryResult
import kr.roadcanvas.app.territory.Geohash
import kr.roadcanvas.app.territory.ResultCellKind
import kr.roadcanvas.app.territory.TerritoryCaptureOutcome
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritoryResultCells
import kr.roadcanvas.app.territory.TerritorySyncSection
import kr.roadcanvas.app.territory.TerritorySyncUiState
import kr.roadcanvas.app.territorymap.TERRITORY_MAP_LABEL
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

const val TERRITORY_RESULT_TITLE = "땅따먹기 결과"
const val TERRITORY_RESULT_LOADING = "영역을 판정하고 있어요"
const val TERRITORY_LOSS_TAG = "territoryLoss"

/**
 * 땅따먹기 결과. 지도에 주행 경로와 차지한 영역을 채운 다각형으로 그리고, 왼쪽 오버레이에 면적·둘레와 거리·시간을 둔다.
 * 영역을 차지하지 못했으면 이유를 안내하고 저장하지 않는다. 땅따먹기는 이름이 필요 없어 이름 입력창을 두지 않는다.
 * 차지한 기록은 [territoryState]로 서버 반영 상태와 빼앗은 상대를 보여주고, [territoryCells]로 이번에 차지한 칸,
 * 빼앗은 칸(이전 소유자 닉네임), 이미 내 칸과 주변 칸을 지도에 나눠 그린다. 영토 지도로 가면 차지한 영역에서 지도를 연다.
 */
@Composable
fun TerritoryResultScreen(
    state: ChallengeResultUiState,
    onRetrySave: () -> Unit,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier,
    territoryState: TerritorySyncUiState? = null,
    territoryCells: TerritoryResultCells? = null,
    onSubmitTerritory: () -> Unit = {},
    onTerritoryMap: (center: RoutePoint?) -> Unit = {}
) {
    val result = (state as? ChallengeResultUiState.Calculated)?.result
    val claimed = result?.territory as? TerritoryResult.Claimed
    val mapCells = remember(territoryCells) { territoryCells?.let(::mapCellsOf).orEmpty() }
    DriverSideOverlay(
        background = {
            RouteMap(
                points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = false,
                    contentPadding = driverSideOverlayContentPadding(),
                    filledArea = claimed?.boundary?.map { RoutePoint(it.latitude, it.longitude) },
                    cells = mapCells
                ),
                modifier = Modifier.fillMaxSize()
            )
            ResultCelebrationLayer(territoryCelebrationOf(territoryState))
        },
        controls = {
            // 내용이 화면 높이를 넘으면 결과 카드만 스크롤하고 버튼은 항상 보이게 둔다.
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = TERRITORY_RESULT_TITLE,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    when (state) {
                        ChallengeResultUiState.Calculating -> StatusText(TERRITORY_RESULT_LOADING)
                        ChallengeResultUiState.Unavailable -> StatusText(RESULT_UNAVAILABLE)
                        is ChallengeResultUiState.Calculated -> {
                            TerritorySummary(state.result)
                            if (state.saveStatus == ResultSaveStatus.NOT_SAVED) {
                                StatusText(TERRITORY_NOT_SAVED)
                            } else {
                                SaveStatus(state.saveStatus, onRetrySave)
                            }
                            territoryCelebrationOf(territoryState)?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag(TERRITORY_OUTCOME_TAG)
                                )
                            }
                            submittedOutcomeOf(territoryState)
                                ?.let { territoryLossText(it.losses) }
                                ?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.testTag(TERRITORY_LOSS_TAG)
                                    )
                                }
                            territoryState?.let { TerritorySyncSection(it, onSubmitTerritory) }
                        }
                    }
                }
            }
            CarButton(
                text = TRY_AGAIN_LABEL,
                onClick = onTryAgain,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                onClick = onModeSelect,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = MY_DRAWINGS_LABEL,
                onClick = onMyDrawings,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = TERRITORY_MAP_LABEL,
                onClick = { onTerritoryMap(claimed?.boundary?.let(::centerOf)) },
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier
    )
}

private fun submittedOutcomeOf(state: TerritorySyncUiState?): TerritoryCaptureOutcome? =
    ((state as? TerritorySyncUiState.Capture)?.state as? TerritoryCaptureState.Submitted)?.outcome

/**
 * 결과 칸을 지도 칸으로 바꾼다. 주변 칸을 먼저 두어 이번 획득의 칸이 위에 그려지게 한다.
 * 이전 소유자 닉네임은 소유자마다 빼앗은 칸 하나에만 붙인다.
 */
internal fun mapCellsOf(cells: TerritoryResultCells): List<MapCell> {
    val labeledOwners = mutableSetOf<String>()
    val nearby = cells.nearby.map {
        MapCell(it.bounds.corners.map(::routePointOf), it.mine, MapCellStyle.FAINT)
    }
    val captured = cells.captured.map { cell ->
        val style = when (cell.kind) {
            ResultCellKind.CAPTURED -> MapCellStyle.CAPTURED
            ResultCellKind.STOLEN -> MapCellStyle.STOLEN
            ResultCellKind.KEPT -> MapCellStyle.KEPT
        }
        val label = cell.previousOwner?.takeIf { labeledOwners.add(it) }
        MapCell(Geohash.bounds(cell.id).corners.map(::routePointOf), true, style, label)
    }
    return nearby + captured
}

private fun routePointOf(point: GeoPoint) = RoutePoint(point.latitude, point.longitude)

/** 차지한 면적을 가장 크게, 둘레와 거리·시간을 그 아래에 둔다. 차지하지 못했으면 면적 대신 이유를 보여준다. */
@Composable
private fun TerritorySummary(result: ChallengeResult) {
    when (val territory = result.territory) {
        is TerritoryResult.Claimed -> {
            Measurement(AREA_LABEL, formatArea(territory.areaSquareMeters), large = true)
            Measurement(PERIMETER_LABEL, formatDistance(territory.perimeterMeters))
        }
        null -> Unit
        else -> territoryFailureText(territory)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Measurement(DISTANCE_LABEL, formatDistance(result.distanceMeters))
        Measurement(DURATION_LABEL, formatElapsed(result.durationMillis))
    }
}

/** 차지한 영역 꼭짓점의 가운데. 영토 지도를 이 위치에서 연다. */
private fun centerOf(boundary: List<GeoPoint>) = RoutePoint(
    boundary.map { it.latitude }.average(),
    boundary.map { it.longitude }.average()
)

@Composable
private fun StatusText(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}
