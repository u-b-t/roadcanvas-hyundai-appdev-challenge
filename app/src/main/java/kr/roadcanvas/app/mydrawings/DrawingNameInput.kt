package kr.roadcanvas.app.mydrawings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.drawing.DRAWING_NAME_MAX_LENGTH
import kr.roadcanvas.app.drawing.DrawingNameValidation
import kr.roadcanvas.app.drawing.normalizeDrawingName
import kr.roadcanvas.app.drawing.validateDrawingName
import kr.roadcanvas.app.ui.components.CarTextInput

const val DRAWING_NAME_LABEL = "그림 이름"
const val DRAWING_NAME_OPTIONAL_LABEL = "그림 이름 (선택)"
const val DRAWING_NAME_PLACEHOLDER = "그림 이름을 입력해 주세요"
const val DRAWING_NAME_OPTIONAL_HINT = "비워 두면 이름 없이 저장해요"
const val DRAWING_NAME_EMPTY_ERROR = "이름을 입력해 주세요"
const val DRAWING_NAME_TOO_LONG_ERROR = "이름은 ${DRAWING_NAME_MAX_LENGTH}자 이하로 입력해 주세요"

/**
 * 입력창이 받는 최대 글자 수. 앞뒤 공백을 넣거나 조금 넘게 입력해도 글자가 사라지지 않고
 * 글자 수와 오류로 알 수 있도록 이름 제한보다 여유를 둔다.
 */
private const val INPUT_LIMIT = DRAWING_NAME_MAX_LENGTH + 10

const val SAVE_NAME_LABEL = "이름 저장"
const val SAVING_NAME_LABEL = "이름 저장 중"
const val NAME_SAVED = "이름을 저장했어요"
const val NAME_SAVE_FAILED = "이름을 저장하지 못했어요"

/** 앞뒤 공백을 뺀 글자 수 표시. 예: `3/20` */
fun drawingNameCounter(input: String): String =
    "${normalizeDrawingName(input).length}/$DRAWING_NAME_MAX_LENGTH"

/**
 * [input]에 보여줄 오류 안내. 너무 긴 이름은 바로, 필수 이름이 빈 경우는 저장을 시도한 뒤([showEmptyError])에만 알린다.
 * 오류가 없으면 null이다.
 */
fun drawingNameError(input: String, required: Boolean, showEmptyError: Boolean): String? =
    when (validateDrawingName(input, required)) {
        DrawingNameValidation.TooLong -> DRAWING_NAME_TOO_LONG_ERROR
        DrawingNameValidation.Empty -> DRAWING_NAME_EMPTY_ERROR.takeIf { showEmptyError }
        is DrawingNameValidation.Valid -> null
    }

/** 그림 이름 입력창. 제목, SDG 입력창, 오류 또는 안내와 글자 수를 보여 준다. 저장 버튼은 호출한 화면이 둔다. */
@Composable
fun DrawingNameInput(
    name: String,
    onNameChange: (String) -> Unit,
    required: Boolean,
    showEmptyError: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val error = drawingNameError(name, required, showEmptyError)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (required) DRAWING_NAME_LABEL else DRAWING_NAME_OPTIONAL_LABEL,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CarTextInput(
            text = name,
            onTextChange = onNameChange,
            placeholder = DRAWING_NAME_PLACEHOLDER,
            maxLength = INPUT_LIMIT,
            isError = error != null,
            enabled = enabled,
            supportingText = error ?: DRAWING_NAME_OPTIONAL_HINT.takeUnless { required },
            counterText = drawingNameCounter(name),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            )
        )
    }
}
