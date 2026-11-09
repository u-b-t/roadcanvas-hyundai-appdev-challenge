package kr.roadcanvas.app.profile

import kr.roadcanvas.app.account.readableNickname
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknameTest {
    @Test
    fun `앞뒤 공백을 제거하고 2~12자를 받는다`() {
        assertEquals(NicknameValidation.Valid("빠른 고래"), validateNickname("  빠른 고래 "))
        assertEquals(NicknameValidation.Valid("ab"), validateNickname("ab"))
        assertEquals(NicknameValidation.Valid("가".repeat(12)), validateNickname("가".repeat(12)))
    }

    @Test
    fun `2자보다 짧거나 공백뿐이면 너무 짧다`() {
        assertEquals(NicknameValidation.TooShort, validateNickname(""))
        assertEquals(NicknameValidation.TooShort, validateNickname("   "))
        assertEquals(NicknameValidation.TooShort, validateNickname(" 가 "))
    }

    @Test
    fun `12자를 넘으면 너무 길다`() {
        assertEquals(NicknameValidation.TooLong, validateNickname("가".repeat(13)))
    }

    @Test
    fun `한글·영문·숫자·공백·하이픈·밑줄 외의 글자는 받지 않는다`() {
        assertEquals(NicknameValidation.Valid("Road_Kim-7"), validateNickname("Road_Kim-7"))
        listOf("고래!", "고래🐳", "ㄱㄴㄷ", "고래\n상어", "<script>").forEach {
            assertEquals(it, NicknameValidation.ForbiddenCharacter, validateNickname(it))
        }
    }

    @Test
    fun `자동 닉네임은 같은 사용자면 같고 닉네임 규칙을 지킨다`() {
        val uids = (0 until 500).map { "uid-$it" }
        uids.forEach { uid ->
            val nickname = readableNickname(uid)
            assertEquals(nickname, readableNickname(uid))
            assertTrue(nickname, validateNickname(nickname) == NicknameValidation.Valid(nickname))
            assertTrue(nickname, Regex("\\S+ \\S+ [1-9][0-9]?").matches(nickname))
        }
        assertTrue(uids.map(::readableNickname).toSet().size > 100)
    }

    @Test
    fun `글자 수는 앞뒤 공백을 빼고 센다`() {
        assertEquals("2/12", nicknameCounter(" 고래 "))
    }
}
