package kr.roadcanvas.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

const val CIRCLE_RESULT_TITLE = "동그라미 챌린지 결과"
const val MY_DRAWINGS_TITLE = "내 그림 목록"

/** 아직 구현하지 않은 화면 자리에 제목만 표시한다. */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
    }
}
