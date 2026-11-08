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
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

const val FREE_DRAWING_RESULT_TITLE = "자유그림 결과"
const val FREE_DRAWING_RESULT_LOADING = "그림을 불러오고 있어요"
const val DRAW_AGAIN_LABEL = "다시 그리기"

/**
 * 자유그림 결과. 점수 없이 그린 경로를 지도 전체에 맞춰 보여주고 거리·시간과 저장 상태를 왼쪽 오버레이에 둔다.
 * 저장한 뒤에는 동그라미 챌린지 결과와 같은 이름 입력창을 보이며, 자유그림은 이름이 필수다.
 */
@Composable
fun FreeDrawingResultScreen(
    state: ChallengeResultUiState,
    onRetrySave: () -> Unit,
    onDrawAgain: () -> Unit,
    onModeSelect: () -> Unit,
    onMyDrawings: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveName: (String) -> Unit = {}
) {
    // 키보드가 열리면 이동 버튼을 숨겨 이름 입력창과 이름 저장 버튼이 남은 높이에 보이게 한다.
    val keyboardOpen = keyboardAsState().value == Keyboard.Opened
    val result = (state as? ChallengeResultUiState.Calculated)?.result
    DriverSideOverlay(
        background = {
            RouteMap(
                points = result?.path.orEmpty().map { RoutePoint(it.latitude, it.longitude) },
                options = RouteMapOptions(
                    showEndpointMarkers = true,
                    inProgress = false,
                    contentPadding = driverSideOverlayContentPadding()
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
                        text = FREE_DRAWING_RESULT_TITLE,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    when (state) {
                        ChallengeResultUiState.Calculating -> Text(
                            text = FREE_DRAWING_RESULT_LOADING,
                            style = MaterialTheme.typography.titleMedium
                        )
                        ChallengeResultUiState.Unavailable -> Text(
                            text = RESULT_UNAVAILABLE,
                            style = MaterialTheme.typography.titleMedium
                        )
                        is ChallengeResultUiState.Calculated -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Measurement(
                                    DISTANCE_LABEL,
                                    formatDistance(state.result.distanceMeters)
                                )
                                Measurement(
                                    DURATION_LABEL,
                                    formatElapsed(state.result.durationMillis)
                                )
                            }
                            SaveStatus(state.saveStatus, onRetrySave)
                            if (state.saveStatus == ResultSaveStatus.SAVED) {
                                ResultNameEditor(state, onSaveName)
                            }
                        }
                    }
                }
            }
            if (!keyboardOpen) {
                CarButton(
                    text = DRAW_AGAIN_LABEL,
                    onClick = onDrawAgain,
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
            }
        },
        modifier = modifier
    )
}
