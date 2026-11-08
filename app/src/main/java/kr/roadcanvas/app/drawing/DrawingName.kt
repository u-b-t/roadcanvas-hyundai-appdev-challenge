package kr.roadcanvas.app.drawing

/** 그림 이름의 최대 글자 수. 공개 작품 이름(title) 규칙과 같다. */
const val DRAWING_NAME_MAX_LENGTH = 20

/** 입력한 그림 이름을 검사한 결과 */
sealed interface DrawingNameValidation {
    /** 저장할 수 있는 이름. 앞뒤 공백을 제거한 [name]이며, 선택 입력에서 비워 두면 null이다. */
    data class Valid(val name: String?) : DrawingNameValidation

    /** 필수 입력인데 비어 있거나 공백뿐이다. */
    data object Empty : DrawingNameValidation

    /** 앞뒤 공백을 제거해도 [DRAWING_NAME_MAX_LENGTH]자를 넘는다. */
    data object TooLong : DrawingNameValidation
}

/** 앞뒤 공백을 제거한 이름. 글자 수 표시와 검사에 쓴다. */
fun normalizeDrawingName(input: String): String = input.trim()

/**
 * [input]을 그림 이름으로 검사한다. 앞뒤 공백을 제거하고 1~[DRAWING_NAME_MAX_LENGTH]자만 허용한다.
 * [required]가 false이면 비어 있거나 공백뿐인 입력은 이름 없음(null)으로 받는다.
 */
fun validateDrawingName(input: String, required: Boolean): DrawingNameValidation {
    val name = normalizeDrawingName(input)
    return when {
        name.isEmpty() && required -> DrawingNameValidation.Empty
        name.isEmpty() -> DrawingNameValidation.Valid(null)
        name.length > DRAWING_NAME_MAX_LENGTH -> DrawingNameValidation.TooLong
        else -> DrawingNameValidation.Valid(name)
    }
}
