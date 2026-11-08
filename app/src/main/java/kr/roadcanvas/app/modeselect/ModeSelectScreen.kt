package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

const val MODE_SELECT_TITLE = "모드 선택"

// 실제 모드 선택 화면은 #11에서 구현한다.
@Composable
fun ModeSelectScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = MODE_SELECT_TITLE, style = MaterialTheme.typography.headlineLarge)
    }
}
