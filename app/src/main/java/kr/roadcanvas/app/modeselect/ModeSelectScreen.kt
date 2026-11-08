package kr.roadcanvas.app.modeselect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val MODE_SELECT_TITLE = "모드 선택"
const val CIRCLE_CHALLENGE_NAME = "동그라미 챌린지"
const val CIRCLE_CHALLENGE_DESCRIPTION = "원을 그리며 달리고 점수를 받아요"
const val FREE_DRAW_NAME = "자유그림"
const val FREE_DRAW_DESCRIPTION = "도로 위에 자유롭게 그려요"
const val COMING_SOON_LABEL = "준비 중"
const val FREE_DRAW_COMING_SOON_NOTICE = "자유그림은 아직 준비 중이에요"
const val MY_DRAWINGS_LABEL = "내 그림"

@Composable
fun ModeSelectScreen(
    onCircleChallengeClick: () -> Unit,
    onMyDrawingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 자유그림은 아직 제공하지 않으므로 누르면 안내만 표시한다.
    var showFreeDrawNotice by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MODE_SELECT_TITLE,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onMyDrawingsClick,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).widthIn(min = 160.dp)
            ) {
                Text(text = MY_DRAWINGS_LABEL, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ModeCard(
                name = CIRCLE_CHALLENGE_NAME,
                description = CIRCLE_CHALLENGE_DESCRIPTION,
                onClick = onCircleChallengeClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ModeCard(
                name = FREE_DRAW_NAME,
                description = FREE_DRAW_DESCRIPTION,
                onClick = { showFreeDrawNotice = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                badge = COMING_SOON_LABEL
            )
        }
        // 안내가 나타나도 카드 크기가 바뀌지 않도록 자리를 미리 잡아 둔다.
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (showFreeDrawNotice) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = FREE_DRAW_COMING_SOON_NOTICE,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val enabledLook = badge == null
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = MIN_TOUCH_TARGET),
        colors = if (enabledLook) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(text = name, style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = description, style = MaterialTheme.typography.titleLarge)
        }
    }
}
