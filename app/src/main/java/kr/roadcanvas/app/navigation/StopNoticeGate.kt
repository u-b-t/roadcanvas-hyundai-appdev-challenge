package kr.roadcanvas.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val GATED_CONTENT_KEY = "gatedContent"

/**
 * [reason]이 있으면 [content] 대신 정차 안내를 보여준다.
 * 가린 동안 화면을 구성에서 빼 대화상자까지 함께 숨기고, 저장 가능한 화면 상태는 남겨 정차하면 그대로 돌아온다.
 * 뒤로 가기도 막아 주행 중에는 화면을 바꾸지 않는다.
 */
@Composable
fun StopNoticeGate(reason: String?, content: @Composable () -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    if (reason == null) {
        stateHolder.SaveableStateProvider(GATED_CONTENT_KEY) { content() }
    } else {
        BackHandler {}
        StopNotice(reason)
    }
}

@Composable
private fun StopNotice(reason: String) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = STOP_NOTICE_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
