package kr.roadcanvas.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shopl.sdg.component.text_input.simple.SDGSimpleTextInput
import com.shopl.sdg.component.text_input.simple.SDGSimpleTextInputField
import com.shopl.sdg.component.text_input.simple.SDGSimpleTextInputState
import com.shopl.sdg.component.text_input.simple.SDGSimpleTextInputStyle
import kr.roadcanvas.app.ui.theme.CAR_TEXT_SCALE
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

/** [CarTextInput]의 입력 영역(터치 영역)에 붙는 테스트 태그 */
const val CAR_TEXT_INPUT_TAG = "carTextInput"

/**
 * 차량용 한 줄 입력창. SDG `SDGSimpleTextInput`을 그대로 쓰되 글자와 높이가 휴대폰 기준이라
 * 밀도를 [CAR_TEXT_SCALE]배 키워 그리고, 입력창을 감싼 영역 높이를 [MIN_TOUCH_TARGET] 이상으로 둔다.
 * 감싼 영역 어디를 눌러도 입력창에 포커스가 가 키보드가 열린다.
 *
 * 아래 줄에 [supportingText](오류면 오류 색)와 [counterText]를 보여 준다.
 */
@Composable
fun CarTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    counterText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val state = when {
        !enabled -> SDGSimpleTextInputState.Disabled
        isError -> SDGSimpleTextInputState.Error
        text.isEmpty() -> SDGSimpleTextInputState.Default
        else -> SDGSimpleTextInputState.Completed
    }
    val density = LocalDensity.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET)
                // 입력창 밖 여백을 눌러도 포커스를 준다. clickable은 입력창 접근성 정보를 합치므로 쓰지 않는다.
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures { focusRequester.requestFocus() }
                }
                .testTag(CAR_TEXT_INPUT_TAG),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * CAR_TEXT_SCALE, density.fontScale)
            ) {
                SDGSimpleTextInput(
                    text = text,
                    placeholder = placeholder,
                    state = state,
                    inputField = SDGSimpleTextInputField.White,
                    style = SDGSimpleTextInputStyle.Outlined,
                    onTextChange = onTextChange,
                    focusRequester = focusRequester,
                    maxLength = maxLength,
                    keyboardOptions = keyboardOptions
                )
            }
        }
        if (supportingText != null || counterText != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = supportingText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                counterText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}
