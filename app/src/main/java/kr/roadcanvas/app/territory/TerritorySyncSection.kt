package kr.roadcanvas.app.territory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale
import kr.roadcanvas.app.challenge.formatArea
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle

const val TERRITORY_SYNC_TITLE = "서버 반영"
const val TERRITORY_SYNC_STATUS_TAG = "territorySyncStatus"
const val SUBMIT_TERRITORY_LABEL = "서버에 제출"
const val RESUBMIT_TERRITORY_LABEL = "다시 제출"
const val NEW_CELLS_LABEL = "새로 차지한 칸"
const val STOLEN_CELLS_LABEL = "빼앗은 칸"
const val OWN_CELLS_LABEL = "이미 내 칸"
const val CAPTURED_AREA_LABEL = "환산 면적"
const val TERRITORY_PENDING_MESSAGE = "연결되면 같은 획득으로 다시 제출해요"
const val TERRITORY_EXPIRED_MESSAGE = "24시간 안에 반영하지 못해 서버 반영을 포기했어요"
const val TERRITORY_NOT_SUBMITTED_MESSAGE = "이 기록은 아직 서버에 제출하지 않았어요"

/** 한 번에 차지할 수 있는 칸 수 안내 */
fun territoryTooLargeText(maxCells: Int) = "한 번에 최대 ${maxCells}칸까지 차지할 수 있어요"

/** 칸 수 표시. `12칸`처럼 쓴다. */
fun formatCells(count: Long) = String.format(Locale.ROOT, "%,d칸", count)

/** 서버 땅따먹기 요청이 실패한 이유 안내 */
val TerritoryFailure.text: String
    get() = when (this) {
        TerritoryFailure.NETWORK -> "네트워크에 연결하지 못했어요"
        TerritoryFailure.AUTH -> "로그인(익명 인증)에 실패했어요"
        TerritoryFailure.CONFLICT -> "다른 사용자의 획득과 겹쳤어요"
        TerritoryFailure.STORAGE -> "기기 기록을 읽거나 저장하지 못했어요"
        TerritoryFailure.UNKNOWN -> "잠시 후 다시 시도해 주세요"
    }

/** 서버에 반영하지 못했고 연결을 기다리는 것만으로는 반영되지 않는 상태 */
private val TerritorySyncUiState.failed: Boolean
    get() = when (this) {
        is TerritorySyncUiState.Failed -> true
        is TerritorySyncUiState.Capture -> when (val capture = state) {
            is TerritoryCaptureState.Pending ->
                capture.failure != null && capture.failure != TerritoryFailure.NETWORK
            is TerritoryCaptureState.TooLarge, is TerritoryCaptureState.Expired -> true
            else -> false
        }
        else -> false
    }

/**
 * 서버 반영 상태 이름. 연결 실패로 기다리는 획득은 대기, 그 밖의 이유로 반영하지 못한 획득은 실패다.
 */
val TerritorySyncUiState.label: String
    get() = when (this) {
        TerritorySyncUiState.Loading -> "확인 중"
        TerritorySyncUiState.NotSubmitted -> "미제출"
        is TerritorySyncUiState.Failed -> "실패"
        is TerritorySyncUiState.Capture -> when (state) {
            is TerritoryCaptureState.Submitting -> "제출 중"
            is TerritoryCaptureState.Submitted -> "반영됨"
            is TerritoryCaptureState.Pending -> if (failed) "실패" else "대기(오프라인)"
            is TerritoryCaptureState.TooLarge -> "영역이 너무 커요"
            is TerritoryCaptureState.Expired -> "서버에 반영하지 못함"
        }
    }

/**
 * 오버레이 카드 안에 넣는 땅따먹기 기록의 서버 반영 상태. 반영되면 칸을 새로 차지한 것, 빼앗은 것, 이미 내 칸으로 나눠
 * 보여주고, 대기 중이거나 실패하면 이유와 다시 제출 버튼을 보인다.
 */
@Composable
fun TerritorySyncSection(state: TerritorySyncUiState, onSubmit: () -> Unit) {
    Text(text = TERRITORY_SYNC_TITLE, style = MaterialTheme.typography.titleMedium)
    val capture = (state as? TerritorySyncUiState.Capture)?.state
    Text(
        text = state.label,
        style = MaterialTheme.typography.titleLarge,
        color = when {
            capture is TerritoryCaptureState.Submitted -> MaterialTheme.colorScheme.primary
            state.failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.testTag(TERRITORY_SYNC_STATUS_TAG)
    )
    when (state) {
        TerritorySyncUiState.Loading -> Unit
        TerritorySyncUiState.NotSubmitted -> Message(TERRITORY_NOT_SUBMITTED_MESSAGE)
        is TerritorySyncUiState.Failed -> Message(state.reason.text)
        is TerritorySyncUiState.Capture -> CaptureDetail(state.state)
    }
    if (state.canSubmit) {
        CarButton(
            text = if (state == TerritorySyncUiState.NotSubmitted) {
                SUBMIT_TERRITORY_LABEL
            } else {
                RESUBMIT_TERRITORY_LABEL
            },
            onClick = onSubmit,
            style = CarButtonStyle.Line,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CaptureDetail(capture: TerritoryCaptureState) {
    when (capture) {
        is TerritoryCaptureState.Submitting -> Unit
        is TerritoryCaptureState.Submitted -> Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val outcome = capture.outcome
            CountRow(NEW_CELLS_LABEL, formatCells(outcome.newCells.toLong()))
            CountRow(STOLEN_CELLS_LABEL, formatCells(outcome.stolenCells.toLong()))
            CountRow(OWN_CELLS_LABEL, formatCells(outcome.ownCells.toLong()))
            CountRow(
                CAPTURED_AREA_LABEL,
                formatArea(territoryAreaOf(outcome.totalCells.toLong()))
            )
        }
        is TerritoryCaptureState.Pending -> {
            capture.failure?.let { Message(it.text) }
            Message(TERRITORY_PENDING_MESSAGE)
        }
        is TerritoryCaptureState.TooLarge -> Message(territoryTooLargeText(capture.maxCells))
        is TerritoryCaptureState.Expired -> Message(TERRITORY_EXPIRED_MESSAGE)
    }
}

@Composable
private fun CountRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
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
