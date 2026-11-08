package kr.roadcanvas.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepWordsTextTest {
    private val joiner = '⁠'

    @Test
    fun `단어 안 글자 사이에만 줄 바꿈 금지 문자를 넣는다`() {
        assertEquals("돌${joiner}아${joiner}와${joiner}요 원${joiner}을", keepWordsTogether("돌아와요 원을"))
    }

    @Test
    fun `띄어쓰기와 줄 바꿈 앞뒤에는 넣지 않는다`() {
        assertEquals(" 가${joiner}나  다\n라 ", keepWordsTogether(" 가나  다\n라 "))
    }

    @Test
    fun `금지 문자를 지우면 원래 문장과 같다`() {
        val text = "이 차량에서는 동그라미 챌린지를 사용할 수 없어요"

        assertEquals(text, keepWordsTogether(text).replace(joiner.toString(), ""))
    }

    @Test
    fun `빈 문장과 한 글자는 그대로 둔다`() {
        assertEquals("", keepWordsTogether(""))
        assertEquals("원", keepWordsTogether("원"))
    }
}
