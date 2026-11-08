package kr.roadcanvas.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

/** 앞뒤 글자 사이에서 줄을 바꾸지 못하게 하는 보이지 않는 문자(U+2060 WORD JOINER) */
private const val WORD_JOINER = '⁠'

/**
 * 한국어는 기본 줄 바꿈이 음절 사이에서도 일어나 "돌 / 아오세요"처럼 단어가 갈라진다.
 * 띄어쓰기에서만 줄이 바뀌도록 공백이 아닌 글자 사이마다 [WORD_JOINER]를 넣는다.
 */
fun keepWordsTogether(text: String): String = buildString(text.length * 2) {
    text.forEachIndexed { index, char ->
        if (index > 0 && !char.isWhitespace() && !text[index - 1].isWhitespace()) {
            append(WORD_JOINER)
        }
        append(char)
    }
}

/**
 * 띄어쓰기에서만 줄을 바꾸는 [Text]. 접근성 서비스와 테스트에는 줄 바꿈 금지 문자를 뺀 원래 [text]를 알린다.
 * 한 단어가 한 줄보다 길면 플랫폼이 단어 안에서 줄을 바꾼다.
 */
@Composable
fun KeepWordsText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    Text(
        text = keepWordsTogether(text),
        style = style,
        color = color,
        onTextLayout = onTextLayout,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }
    )
}
