package kr.roadcanvas.app.artwork

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle

const val ARTWORK_SECTION_TITLE = "작품 공개"
const val ARTWORK_STATUS_TAG = "artworkPublishStatus"
const val ARTWORK_NAME_REQUIRED = "이름을 저장하면 작품으로 공개할 수 있어요"
const val PUBLISH_LABEL = "작품 공개하기"
const val RETRY_PUBLISH_LABEL = "다시 공개하기"
const val UNPUBLISH_LABEL = "비공개로 전환"
const val UNPUBLISHING_LABEL = "비공개로 바꾸는 중"
const val PUBLISH_FAILED = "작품을 공개하지 못했어요"
const val UNPUBLISH_FAILED = "비공개로 바꾸지 못했어요"
const val TITLE_SYNC_FAILED = "공개 작품의 이름은 바꾸지 못했어요"
const val PUBLISH_CONSENT_TITLE = "작품을 공개할까요?"
const val PUBLISH_CONSENT_MESSAGE =
    "출발점과 도착점을 포함한 경로, 닉네임과 작품 이름이 다른 사용자에게 공개돼요. " +
        "비공개로 다시 바꿀 수 있지만 서버에서 영구 삭제되지는 않아요."
const val PUBLISH_CONSENT_CONFIRM = "동의하고 공개"
const val PUBLISH_CONSENT_CANCEL = "취소"

/** 공개 상태 이름. 화면의 상태 표시에 쓴다. */
val PublishStatus.label: String
    get() = when (this) {
        PublishStatus.UNPUBLISHED -> "미공개"
        PublishStatus.PUBLISHING -> "등록 중"
        PublishStatus.PUBLISHED -> "공개됨"
        PublishStatus.FAILED -> "실패"
    }

/** 공개하지 못한 이유 안내 */
val PublishFailure.text: String
    get() = when (this) {
        PublishFailure.AUTH -> "계정을 확인하지 못했어요"
        PublishFailure.NETWORK -> "네트워크에 연결하지 못했어요"
        PublishFailure.INCOMPLETE -> "경로 일부를 올리지 못했어요"
        PublishFailure.INVALID -> "작품 이름이나 경로가 공개 조건에 맞지 않아요"
        PublishFailure.STORAGE -> "기기 기록을 저장하지 못했어요"
        PublishFailure.UNKNOWN -> "잠시 후 다시 시도해 주세요"
    }

/** 공개 섹션의 조작. 화면이 ViewModel 함수를 연결한다. */
data class ArtworkPublishActions(
    val onRequestPublish: () -> Unit = {},
    val onConfirmPublish: () -> Unit = {},
    val onCancelConsent: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onUnpublish: () -> Unit = {}
)

/** 공개 상태와 버튼을 보이고, 동의 안내를 열었으면 대화상자를 띄운다. */
@Composable
fun ArtworkPublishPanel(state: ArtworkPublishUiState, actions: ArtworkPublishActions) {
    ArtworkPublishSection(
        state = state,
        onRequestPublish = actions.onRequestPublish,
        onRetry = actions.onRetry,
        onUnpublish = actions.onUnpublish
    )
    if (state.consentVisible) {
        PublishConsentDialog(
            onConfirm = actions.onConfirmPublish,
            onCancel = actions.onCancelConsent
        )
    }
}

/**
 * 오버레이 카드 안에 넣는 작품 공개 상태와 버튼. 공개는 [onRequestPublish]로 동의 안내를 연 뒤에만 시작한다.
 * 실패하면 이유와 다시 공개 버튼을, 공개 중이면 비공개 전환 버튼을 보인다.
 */
@Composable
fun ArtworkPublishSection(
    state: ArtworkPublishUiState,
    onRequestPublish: () -> Unit,
    onRetry: () -> Unit,
    onUnpublish: () -> Unit
) {
    Text(text = ARTWORK_SECTION_TITLE, style = MaterialTheme.typography.titleMedium)
    Text(
        text = state.status.label,
        style = MaterialTheme.typography.titleLarge,
        color = when (state.status) {
            PublishStatus.PUBLISHED -> MaterialTheme.colorScheme.primary
            PublishStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.testTag(ARTWORK_STATUS_TAG)
    )
    when (state.status) {
        PublishStatus.UNPUBLISHED -> if (state.canPublish) {
            CarButton(
                text = PUBLISH_LABEL,
                onClick = onRequestPublish,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Message(ARTWORK_NAME_REQUIRED)
        }
        PublishStatus.PUBLISHING -> Unit
        PublishStatus.PUBLISHED -> {
            state.titleSyncFailure?.let { ErrorMessage(TITLE_SYNC_FAILED) }
            state.unpublishFailure?.let { ErrorMessage("$UNPUBLISH_FAILED. ${it.text}") }
            CarButton(
                text = if (state.unpublishing) UNPUBLISHING_LABEL else UNPUBLISH_LABEL,
                onClick = onUnpublish,
                enabled = !state.unpublishing,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
        }
        PublishStatus.FAILED -> {
            ErrorMessage(state.failure?.let { "$PUBLISH_FAILED. ${it.text}" } ?: PUBLISH_FAILED)
            if (state.canPublish) {
                CarButton(
                    text = RETRY_PUBLISH_LABEL,
                    onClick = onRetry,
                    style = CarButtonStyle.Line,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Message(ARTWORK_NAME_REQUIRED)
            }
        }
    }
}

/** 공개 범위를 안내하고 명시적으로 동의해야만 공개하는 대화상자 */
@Composable
fun PublishConsentDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        shape = SDGCornerRadius.BoxRadius.Radius20,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(text = PUBLISH_CONSENT_TITLE, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Text(
                text = PUBLISH_CONSENT_MESSAGE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = { CarButton(text = PUBLISH_CONSENT_CONFIRM, onClick = onConfirm) },
        dismissButton = {
            CarButton(
                text = PUBLISH_CONSENT_CANCEL,
                onClick = onCancel,
                style = CarButtonStyle.Neutral
            )
        }
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ErrorMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error
    )
}
