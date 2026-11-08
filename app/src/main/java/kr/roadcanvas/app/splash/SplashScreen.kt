package kr.roadcanvas.app.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

const val SPLASH_DURATION_MILLIS = 2_000L
const val APP_NAME = "RoadCanvas"

@Composable
fun SplashScreen(durationMillis: Long, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(durationMillis) {
        delay(durationMillis)
        currentOnFinished()
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = APP_NAME, style = MaterialTheme.typography.displayMedium)
    }
}
