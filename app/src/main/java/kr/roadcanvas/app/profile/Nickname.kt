package kr.roadcanvas.app.profile

/** 바꿀 수 있는 닉네임의 글자 수 범위 */
const val NICKNAME_MIN_LENGTH = 2
const val NICKNAME_MAX_LENGTH = 12

/** 닉네임에 쓸 수 있는 글자: 완성형 한글, 영문, 숫자, 공백, `-`, `_` */
private val NICKNAME_ALLOWED = Regex("[가-힣a-zA-Z0-9 _-]+")

/** 입력한 닉네임을 검사한 결과 */
sealed interface NicknameValidation {
    /** 쓸 수 있는 닉네임. 앞뒤 공백을 제거한 [nickname]이다. */
    data class Valid(val nickname: String) : NicknameValidation

    /** 앞뒤 공백을 제거하면 [NICKNAME_MIN_LENGTH]자보다 짧다. */
    data object TooShort : NicknameValidation

    /** 앞뒤 공백을 제거해도 [NICKNAME_MAX_LENGTH]자를 넘는다. */
    data object TooLong : NicknameValidation

    /** 한글·영문·숫자·공백·`-`·`_`가 아닌 글자(기호, 이모지, 자모만 쓴 글자 등)가 있다. */
    data object ForbiddenCharacter : NicknameValidation
}

/** 앞뒤 공백을 제거한 닉네임. 글자 수 표시와 검사에 쓴다. */
fun normalizeNickname(input: String): String = input.trim()

/** [input]을 닉네임으로 검사한다. 앞뒤 공백을 제거하고 2~12자, 허용한 글자만 받는다. */
fun validateNickname(input: String): NicknameValidation {
    val nickname = normalizeNickname(input)
    return when {
        nickname.length < NICKNAME_MIN_LENGTH -> NicknameValidation.TooShort
        nickname.length > NICKNAME_MAX_LENGTH -> NicknameValidation.TooLong
        !NICKNAME_ALLOWED.matches(nickname) -> NicknameValidation.ForbiddenCharacter
        else -> NicknameValidation.Valid(nickname)
    }
}
