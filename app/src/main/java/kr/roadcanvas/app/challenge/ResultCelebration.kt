package kr.roadcanvas.app.challenge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shopl.sdg_common.foundation.SDGColor
import com.shopl.sdg_common.foundation.SDGCornerRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kr.roadcanvas.app.artwork.PublishStatus
import kr.roadcanvas.app.territory.TerritoryCaptureState
import kr.roadcanvas.app.territory.TerritorySyncUiState
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

/** 축하 연출 전체 길이. 이 시간이 지나면 연출이 사라진다. */
const val CELEBRATION_DURATION_MILLIS = 1_600
const val CELEBRATION_TAG = "resultCelebration"
const val BEST_RECORD_BADGE = "최고 기록"
const val BEST_RECORD_BADGE_TAG = "bestRecordBadge"
const val NEW_BEST_CELEBRATION = "최고 기록 경신!"
const val PUBLISHED_CELEBRATION = "작품을 공개했어요"
const val TERRITORY_OUTCOME_TAG = "territoryOutcome"

/** 이 화면에서 보여 준 축하 연출 문구. 접근성 서비스에는 노출되지 않는다. */
val CelebrationPlayedKey = SemanticsPropertyKey<String>("CelebrationPlayed")
var SemanticsPropertyReceiver.celebrationPlayed by CelebrationPlayedKey

/** 서버 반영에서 다른 사용자의 칸을 빼앗았을 때 연출 문구 */
fun stolenCellsCelebration(count: Int) = "${count}칸을 빼앗았어요"

/** 서버 반영에서 새 칸을 차지했을 때 연출 문구 */
fun newCellsCelebration(count: Int) = "${count}칸을 새로 차지했어요"

/** 같은 모드의 이전 최고 점수를 넘은 0점이 아닌 결과. 첫 기록도 최고 기록이다. */
fun isBestRecord(score: Int, comparison: BestRecordComparison?): Boolean = score > 0 &&
    (comparison is BestRecordComparison.NewRecord || comparison is BestRecordComparison.FirstRecord)

/** 동그라미 결과 연출 문구. 최고 기록을 경신했을 때만 있다. */
fun circleCelebrationOf(state: ChallengeResultUiState): String? {
    val calculated = state as? ChallengeResultUiState.Calculated ?: return null
    val score = calculated.result.score?.score ?: return null
    return if (isBestRecord(score, calculated.bestComparison)) NEW_BEST_CELEBRATION else null
}

/** 땅따먹기 결과 연출 문구. 서버에 반영되어 빼앗은 칸이 있으면 빼앗은 칸, 없으면 새 칸 수를 알린다. */
fun territoryCelebrationOf(state: TerritorySyncUiState?): String? {
    val submitted = (state as? TerritorySyncUiState.Capture)?.state
        as? TerritoryCaptureState.Submitted ?: return null
    val outcome = submitted.outcome
    return when {
        outcome.stolenCells > 0 -> stolenCellsCelebration(outcome.stolenCells)
        outcome.newCells > 0 -> newCellsCelebration(outcome.newCells)
        else -> null
    }
}

/**
 * 결과 화면 지도 위에 [message]를 한 번 크게 띄웠다가 [CELEBRATION_DURATION_MILLIS] 안에 사라지게 하는 연출.
 * 문구가 튀어나오며 둘레로 색 조각이 퍼지고 마지막에 흐려진다. 터치 처리가 없어 아래 지도와 버튼 조작을 막지 않는다.
 * 같은 문구는 화면이 다시 만들어져도 한 번만 보여 준다.
 */
@Composable
fun ResultCelebration(message: String?, modifier: Modifier = Modifier) {
    var played by rememberSaveable { mutableStateOf<String?>(null) }
    var showing by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(message) {
        if (message == null || message == played) return@LaunchedEffect
        played = message
        showing = message
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(durationMillis = CELEBRATION_DURATION_MILLIS, easing = LinearEasing)
        )
        showing = null
    }
    // 연출이 끝난 뒤에도 보여 준 문구를 의미 속성으로 남겨 테스트가 확인할 수 있게 한다.
    Box(
        modifier = modifier
            .testTag(CELEBRATION_TAG)
            .semantics { played?.let { celebrationPlayed = it } },
        contentAlignment = Alignment.Center
    ) {
        val text = showing ?: return@Box
        Confetti(progress = progress.value, modifier = Modifier.size(ConfettiSize))
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier
                .graphicsLayer {
                    val p = progress.value
                    val scale = popScale(p)
                    scaleX = scale
                    scaleY = scale
                    alpha = fadeAlpha(p)
                }
                .background(MaterialTheme.colorScheme.primary, SDGCornerRadius.BoxRadius.Radius20)
                .padding(horizontal = 32.dp, vertical = 20.dp)
        )
    }
}

/**
 * 작품 공개 연출 문구. 이 화면에서 공개 전 상태를 본 뒤 공개됨이 되었을 때만 있다.
 * 이미 공개한 기록을 다시 열었을 때는 연출하지 않는다.
 */
@Composable
fun rememberPublishCelebration(status: PublishStatus?): String? {
    var sawUnpublished by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status != null && status != PublishStatus.PUBLISHED) sawUnpublished = true
    }
    return if (sawUnpublished && status == PublishStatus.PUBLISHED) PUBLISHED_CELEBRATION else null
}

/** 결과 화면 배경 위에 겹쳐 왼쪽 조작 영역을 뺀 지도 가운데에 [ResultCelebration]을 띄운다. */
@Composable
fun ResultCelebrationLayer(message: String?) {
    Box(modifier = Modifier.fillMaxSize().padding(driverSideOverlayContentPadding())) {
        ResultCelebration(message = message, modifier = Modifier.align(Alignment.Center))
    }
}

/** 최고 기록 배지. 점수 옆에 둔다. */
@Composable
fun BestRecordBadge(modifier: Modifier = Modifier) {
    Text(
        text = BEST_RECORD_BADGE,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = modifier
            .testTag(BEST_RECORD_BADGE_TAG)
            .background(SDGColor.Secondary400, SDGCornerRadius.BoxRadius.Radius20)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

private val ConfettiSize = 360.dp
private const val CONFETTI_COUNT = 18
private val ConfettiColors = listOf(
    SDGColor.Primary300,
    SDGColor.Secondary400,
    SDGColor.Primary200,
    SDGColor.Primary400
)

/** 가운데에서 바깥으로 퍼지며 흐려지는 색 조각 */
@Composable
private fun Confetti(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val maxRadius = size.minDimension / 2
        val travel = (progress / SPREAD_END).coerceAtMost(1f)
        val distance = maxRadius * (0.25f + 0.75f * (1f - (1f - travel) * (1f - travel)))
        val alpha = fadeAlpha(progress)
        repeat(CONFETTI_COUNT) { index ->
            val angle = 2 * PI * index / CONFETTI_COUNT
            val reach = if (index % 2 == 0) 1f else 0.8f
            drawCircle(
                color = ConfettiColors[index % ConfettiColors.size],
                radius = if (index % 3 == 0) 9.dp.toPx() else 6.dp.toPx(),
                center = center + Offset(
                    (cos(angle) * distance * reach).toFloat(),
                    (sin(angle) * distance * reach).toFloat()
                ),
                alpha = alpha
            )
        }
    }
}

/** 색 조각이 끝까지 퍼지는 진행 비율 */
private const val SPREAD_END = 0.6f

/** 처음 20% 동안 작게 시작해 살짝 커졌다가 제 크기로 돌아온다. */
private fun popScale(progress: Float): Float = when {
    progress < 0.12f -> 0.6f + 0.55f * (progress / 0.12f)
    progress < 0.2f -> 1.15f - 0.15f * ((progress - 0.12f) / 0.08f)
    else -> 1f
}

/** 마지막 25% 동안 흐려져 사라진다. */
private fun fadeAlpha(progress: Float): Float =
    if (progress < 0.75f) 1f else ((1f - progress) / 0.25f).coerceIn(0f, 1f)
