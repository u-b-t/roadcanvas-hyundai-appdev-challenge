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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.enums.Keyboard
import com.shopl.sdg_common.util.keyboardAsState
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.MY_DRAWINGS_LABEL
import kr.roadcanvas.app.score.GeoPoint
import kr.roadcanvas.app.score.TerritoryResult
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

/**
 * 땅따먹기 결과. 지도에 주행 경로와 차지한 영역을 채운 다각형으로 그리고, 왼쪽 오버레이에 면적·둘레와 거리·시간을 둔다.
 * 영역을 차지하지 못했으면 이유를 안내하고 저장하지 않는다. 저장한 뒤에는 선택 입력인 이름 입력창을 보인다.
 * 차지한 기록은 [territoryState]로 서버 반영 상태를 보여주고, 영토 지도로 가면 차지한 영역에서 지도를 연다.
 */
@Composable
fun TerritoryResultScreen(
    state: ChallengeResultUiState,
    onRetrySave: () -> Unit,
    onTryAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveName: (String) -> Unit = {},
    territoryState: TerritorySyncUiState? = null,
    onSubmitTerritory: () -> Unit = {},
    onTerritoryMap: (center: RoutePoint?) -> Unit = {}
) {
    // 키보드가 열리면 이동 버튼을 숨겨 이름 입력창과 이름 저장 버튼이 남은 높이에 보이게 한다.
    val keyboardOpen = keyboardAsState().value == Keyboard.Opened
    val result = (state as? ChallengeResultUiState.Calculated)?.result
    val claimed = result?.territory as? TerritoryResult.Claimed
    DriverSideOverlay(
        background = {
            RouteMap(
                points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = false,
                    contentPadding = driverSideOverlayContentPadding(),
                    filledArea = claimed?.boundary?.map { RoutePoint(it.latitude, it.longitude) }
                ),
                modifier = Modifier.fillMaxSize()
            )
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
                            territoryState?.let { TerritorySyncSection(it, onSubmitTerritory) }
                            if (state.saveStatus == ResultSaveStatus.SAVED) {
                                ResultNameEditor(state, onSaveName)
                            }
                        }
                    }
                }
            }
            if (!keyboardOpen) {
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
            }
        },
        modifier = modifier
    )
}

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
