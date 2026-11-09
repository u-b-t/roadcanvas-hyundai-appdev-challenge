package kr.roadcanvas.app.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import java.text.NumberFormat
import java.util.Locale
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.BACK_TO_MODE_SELECT_LABEL
import kr.roadcanvas.app.mydrawings.formatScore
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.CarTextInput
import kr.roadcanvas.app.ui.components.DriverSideLayout
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val PROFILE_LABEL = "프로필"
const val PROFILE_TITLE = "프로필"
const val NICKNAME_LABEL = "닉네임"
const val NICKNAME_PLACEHOLDER = "닉네임을 입력해 주세요"
const val NICKNAME_HINT = "한글·영문·숫자 ${NICKNAME_MIN_LENGTH}~${NICKNAME_MAX_LENGTH}자"
const val NICKNAME_TOO_SHORT_ERROR = "닉네임은 ${NICKNAME_MIN_LENGTH}자 이상 입력해 주세요"
const val NICKNAME_TOO_LONG_ERROR = "닉네임은 ${NICKNAME_MAX_LENGTH}자 이하로 입력해 주세요"
const val NICKNAME_FORBIDDEN_ERROR = "한글, 영문, 숫자, 공백, -, _만 쓸 수 있어요"
const val SAVE_NICKNAME_LABEL = "닉네임 저장"
const val SAVING_NICKNAME_LABEL = "저장 중"
const val NICKNAME_SAVED = "닉네임을 바꿨어요"
const val NICKNAME_SAVE_FAILED = "닉네임을 바꾸지 못했어요"
const val PROFILE_SUMMARY_TITLE = "내 기록"
const val PROFILE_CIRCLE_BEST_LABEL = "동그라미 최고 점수"
const val PROFILE_TERRITORY_LABEL = "내 땅"
const val PROFILE_ARTWORK_LABEL = "공개 작품"
const val PROFILE_LOADING = "불러오는 중"
const val PROFILE_REFRESH_LABEL = "새로고침"
const val PROFILE_ANONYMOUS_NOTICE =
    "익명 계정이라 앱 데이터를 지우거나 기기를 바꾸면 닉네임과 서버 기록을 복구할 수 없어요"
const val PROFILE_SUMMARY_ROW_TAG = "profileSummaryRow"
const val PROFILE_NICKNAME_TAG = "profileNickname"

/** 아직 기록이 없는 항목에 보여주는 값 */
const val PROFILE_NO_RECORD = "기록 없음"

/** 입력창이 받는 최대 글자 수. 조금 넘게 입력해도 글자가 사라지지 않고 오류로 알 수 있게 여유를 둔다. */
private const val INPUT_LIMIT = NICKNAME_MAX_LENGTH + 8

fun formatCellCount(count: Long): String =
    "${NumberFormat.getIntegerInstance(Locale.KOREA).format(count)}칸"

fun formatArtworkCount(count: Int): String = "${count}개"

/** 앞뒤 공백을 뺀 글자 수 표시. 예: `5/12` */
fun nicknameCounter(input: String): String =
    "${normalizeNickname(input).length}/$NICKNAME_MAX_LENGTH"

val NicknameValidation.errorText: String?
    get() = when (this) {
        NicknameValidation.TooShort -> NICKNAME_TOO_SHORT_ERROR
        NicknameValidation.TooLong -> NICKNAME_TOO_LONG_ERROR
        NicknameValidation.ForbiddenCharacter -> NICKNAME_FORBIDDEN_ERROR
        is NicknameValidation.Valid -> null
    }

val ProfileFailure.text: String
    get() = when (this) {
        ProfileFailure.NETWORK -> "네트워크에 연결하지 못했어요"
        ProfileFailure.AUTH -> "로그인(익명 인증)에 실패했어요"
        ProfileFailure.UNKNOWN -> "프로필을 불러오지 못했어요"
    }

/** 프로필 화면에 계정과 서버 프로필을 연결한다. */
@Composable
fun ProfileRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProfileViewModel(context.appContainer.profile) }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSave = viewModel::save,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * 왼쪽 조작 영역에 현재 닉네임, 닉네임 입력창과 저장·뒤로 버튼을 두고,
 * 오른쪽에 내 기록 요약과 익명 계정 안내를 보여준다.
 * 너무 긴 닉네임은 입력하는 동안 바로, 나머지 검사 오류는 저장을 시도한 뒤 알린다.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val saving = state.save == NicknameSaveState.Saving
    val liveError = validateNickname(state.input).takeIf { it == NicknameValidation.TooLong }
    val error = (state.inputError ?: liveError)?.errorText
    DriverSideLayout(
        controls = {
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = PROFILE_TITLE, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = state.account?.nickname ?: "-",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(PROFILE_NICKNAME_TAG)
                    )
                    Text(
                        text = NICKNAME_LABEL,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CarTextInput(
                        text = state.input,
                        onTextChange = onInputChange,
                        placeholder = NICKNAME_PLACEHOLDER,
                        maxLength = INPUT_LIMIT,
                        isError = error != null,
                        enabled = !saving,
                        supportingText = error ?: NICKNAME_HINT,
                        counterText = nicknameCounter(state.input),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done
                        )
                    )
                    SaveMessage(state.save)
                }
            }
            CarButton(
                text = if (saving) SAVING_NICKNAME_LABEL else SAVE_NICKNAME_LABEL,
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                onClick = onBack,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        },
        content = {
            OverlayCard(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = PROFILE_SUMMARY_TITLE,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Summary(state.summary, onRefresh)
                    Text(
                        text = PROFILE_ANONYMOUS_NOTICE,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SDGCornerRadius.BoxRadius.Radius12)
                            .background(SDGColor.Neutral100)
                            .padding(16.dp)
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SaveMessage(save: NicknameSaveState) {
    when (save) {
        NicknameSaveState.Saved -> Text(
            text = NICKNAME_SAVED,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        is NicknameSaveState.Failed -> Text(
            text = "$NICKNAME_SAVE_FAILED: ${save.reason.text}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        else -> Unit
    }
}

/** 동그라미 최고 점수, 내 땅 칸 수와 공개 작품 수. 실패하면 이유와 새로고침 버튼을 보여준다. */
@Composable
private fun Summary(summary: ProfileSummaryState, onRefresh: () -> Unit) {
    when (summary) {
        ProfileSummaryState.Loading -> Text(
            text = PROFILE_LOADING,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        is ProfileSummaryState.Failed -> {
            Text(
                text = summary.reason.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            CarButton(
                text = PROFILE_REFRESH_LABEL,
                onClick = onRefresh,
                style = CarButtonStyle.Line
            )
        }
        is ProfileSummaryState.Loaded -> {
            val value = summary.summary
            SummaryRow(
                PROFILE_CIRCLE_BEST_LABEL,
                value.circleBestScore?.let(::formatScore) ?: PROFILE_NO_RECORD
            )
            SummaryRow(PROFILE_TERRITORY_LABEL, formatCellCount(value.territoryCellCount))
            SummaryRow(PROFILE_ARTWORK_LABEL, formatArtworkCount(value.publishedArtworkCount))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(SDGCornerRadius.BoxRadius.Radius12)
            .background(SDGColor.Primary50)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(PROFILE_SUMMARY_ROW_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
