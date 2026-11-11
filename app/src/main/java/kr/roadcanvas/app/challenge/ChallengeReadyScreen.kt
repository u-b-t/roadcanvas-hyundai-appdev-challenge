package kr.roadcanvas.app.challenge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shopl.sdg_resource.R as SdgR
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.map.MapCell
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.territory.NearbyTerritory
import kr.roadcanvas.app.territory.text
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.KeepWordsText
import kr.roadcanvas.app.ui.components.NavigationIcon
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

const val CIRCLE_READY_TITLE = "동그라미 챌린지 준비"
const val FREE_DRAWING_READY_TITLE = "자유그림 준비"
const val TERRITORY_READY_TITLE = "땅따먹기 준비"
const val CHALLENGE_GOAL = "원을 그리고 출발점으로 돌아와요"
const val FREE_DRAWING_GOAL = "달린 길이 그대로 그림이 돼요"
const val TERRITORY_GOAL = "한 바퀴 돌아 출발점으로 오면 둘러싼 땅을 차지해요"
const val CHALLENGE_RULE_START_STOP = "정차한 뒤 시작하고 끝내요"
const val CHALLENGE_RULE_NO_PAUSE = "도중에 일시정지할 수 없어요"

const val VOICE_GUIDE_ON_LABEL = "음성 안내 켜짐"
const val VOICE_GUIDE_OFF_LABEL = "음성 안내 꺼짐"

/** 땅따먹기 준비 화면의 주변 영토 요약 테스트 태그 */
const val NEARBY_TERRITORY_TAG = "nearby_territory"

/** 목표·진행 방법 안내 문장 옆 아이콘의 테스트 태그 */
const val READY_GUIDE_ICON_TAG = "ready_guide_icon"

/** 화면에서 가장 큰 조작 요소가 되도록 다른 버튼([MIN_TOUCH_TARGET])보다 키운 시작 버튼 높이 */
val START_BUTTON_HEIGHT = 112.dp

private val GuideIconSize = 32.dp

/**
 * 목표와 진행 방법. 운전 중에도 한눈에 읽도록 아이콘과 짧은 문장 세 개로 줄인다.
 * 목표만 모드마다 다르고 진행 방법은 같다.
 */
private fun readyGuidesOf(mode: DrawingMode) = listOf(
    when (mode) {
        DrawingMode.CIRCLE -> SdgR.drawable.ic_retry to CHALLENGE_GOAL
        DrawingMode.FREE_DRAWING -> SdgR.drawable.ic_common_edit to FREE_DRAWING_GOAL
        DrawingMode.TERRITORY -> SdgR.drawable.ic_retry to TERRITORY_GOAL
    },
    SdgR.drawable.ic_common_play to CHALLENGE_RULE_START_STOP,
    SdgR.drawable.ic_ban to CHALLENGE_RULE_NO_PAUSE
)

const val READY_STATUS_READY = "시작할 준비가 되었어요"
const val READY_STATUS_LOCATING = "위치 확인 중"
const val READY_STATUS_WAITING_FOR_STOP = "정차한 뒤 시작할 수 있어요"
const val READY_STATUS_UNSUPPORTED = "이 차량에서는 동그라미 챌린지를 사용할 수 없어요"
const val FREE_DRAWING_STATUS_UNSUPPORTED = "이 차량에서는 자유그림을 사용할 수 없어요"
const val TERRITORY_STATUS_UNSUPPORTED = "이 차량에서는 땅따먹기를 사용할 수 없어요"
const val READY_STATUS_PERMISSION_REQUIRED = "위치 권한이 필요해요"
const val READY_STATUS_PERMISSION_IN_SETTINGS = "설정에서 위치 권한을 허용해 주세요"
const val READY_STATUS_BLOCKED = "위치 접근이 차단되어 있어요"
const val READY_STATUS_ERROR = "위치를 확인하지 못했어요"
const val BLOCKED_NOTICE_MESSAGE = "차량 설정에서 위치 접근을 허용한 뒤 다시 시도해 주세요"

const val TODAY_TOPIC_LABEL = "오늘의 주제"
const val NEXT_TOPIC_LABEL = "다른 주제"
const val TODAY_TOPIC_TAG = "todayTopic"

const val START_LABEL = "시작"
const val BACK_TO_MODE_SELECT_LABEL = "홈으로"
const val REQUEST_PERMISSION_LABEL = "권한 요청"
const val OPEN_SETTINGS_LABEL = "설정 열기"
const val SHOW_BLOCKED_NOTICE_LABEL = "차단 안내 보기"
const val RETRY_LABEL = "다시 시도"
private const val CONFIRM_LABEL = "확인"
private const val TAG = "ChallengeReady"

/** 준비 화면에 시작 조건 구독과 권한 요청을 연결한다. 시작 조건은 모든 모드가 같다. */
@Composable
fun ChallengeReadyRoute(
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mode: DrawingMode = DrawingMode.CIRCLE
) {
    val context = LocalContext.current
    val viewModel: ChallengeReadyViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = context.appContainer
                ChallengeReadyViewModel(
                    container.locationProvider,
                    container.drivingRestrictionProvider,
                    container.territoryGame.takeIf { mode == DrawingMode.TERRITORY }
                )
            }
        }
    )
    // 화면이 멈추면 구독자가 없어져 위치와 주행 규제 구독도 해제된다.
    val status by viewModel.status.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val nearbyTerritory by viewModel.nearbyTerritory.collectAsStateWithLifecycle()
    val voiceGuide = context.appContainer.voiceGuide
    val voiceGuideEnabled by voiceGuide.enabled.collectAsStateWithLifecycle()
    // 자유그림만 오늘의 주제를 보여 주고, 시작하면 그때 주제를 결과 화면의 이름 제안으로 남긴다.
    val topicPicker = context.appContainer.drawingTopicPicker
    val topic by topicPicker.topic.collectAsStateWithLifecycle()
    val freeDrawing = mode == DrawingMode.FREE_DRAWING

    var requestedPermissions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val activity = context.findActivity()
        viewModel.onPermissionResult(
            granted = results.isNotEmpty() && results.values.all { it },
            shouldShowRationale = requestedPermissions.any {
                activity?.shouldShowRequestPermissionRationale(it) == true
            }
        )
    }

    ChallengeReadyScreen(
        status = status,
        onStart = {
            if (freeDrawing) topicPicker.startWith(topic)
            onStart()
        },
        onBack = onBack,
        onRequestPermission = { permissions ->
            requestedPermissions = permissions
            permissionLauncher.launch(permissions.toTypedArray())
        },
        onOpenSettings = { context.openAppSettings() },
        onShowBlockedNotice = {
            context.findActivity()?.let(viewModel::showAccessBlockedNotice) == true
        },
        onRetry = viewModel::retry,
        modifier = modifier,
        currentLocation = currentLocation?.let { RoutePoint(it.latitude, it.longitude) },
        mode = mode,
        nearbyTerritory = nearbyTerritory,
        voiceGuideEnabled = voiceGuideEnabled.takeIf { mode == DrawingMode.TERRITORY },
        onVoiceGuideChange = voiceGuide::setEnabled,
        topic = topic.takeIf { freeDrawing },
        onNextTopic = topicPicker::next
    )
}

/** [topic]이 있으면 안내 아래에 오늘의 주제와 다른 주제 버튼을 둔다. */
@Composable
fun ChallengeReadyScreen(
    status: ChallengeReadyStatus,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onRequestPermission: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentLocation: RoutePoint? = null,
    onShowBlockedNotice: () -> Boolean = { false },
    mode: DrawingMode = DrawingMode.CIRCLE,
    nearbyTerritory: NearbyTerritory? = null,
    voiceGuideEnabled: Boolean? = null,
    onVoiceGuideChange: (Boolean) -> Unit = {},
    topic: String? = null,
    onNextTopic: () -> Unit = {}
) {
    // 차량의 차단 안내를 띄우지 못하면 앱 안에서 같은 내용을 안내한다.
    var showBlockedNotice by rememberSaveable { mutableStateOf(false) }
    val nearbyCells = (nearbyTerritory as? NearbyTerritory.Loaded)?.cells
    val nearbyMapCells = remember(nearbyCells) {
        nearbyCells.orEmpty().map { cell ->
            MapCell(cell.bounds.corners.map { RoutePoint(it.latitude, it.longitude) }, cell.mine)
        }
    }

    DriverSideOverlay(
        background = {
            RouteMap(
                points = listOfNotNull(currentLocation),
                options = RouteMapOptions(
                    showCurrentLocation = true,
                    contentPadding = driverSideOverlayContentPadding(),
                    cells = nearbyMapCells
                ),
                modifier = Modifier.fillMaxSize()
            )
        },
        controls = {
            // 내용이 화면 높이를 넘으면 안내 카드만 스크롤하고 상태와 시작 버튼은 항상 보이게 둔다.
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KeepWordsText(
                        text = readyTitleOf(mode),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    readyGuidesOf(mode).forEach { (icon, guide) ->
                        GuideRow(icon = icon, text = guide)
                    }
                    nearbyTerritory?.let {
                        KeepWordsText(
                            text = it.text,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag(NEARBY_TERRITORY_TAG)
                        )
                    }
                    voiceGuideEnabled?.let { enabled ->
                        CarButton(
                            text = if (enabled) VOICE_GUIDE_ON_LABEL else VOICE_GUIDE_OFF_LABEL,
                            onClick = { onVoiceGuideChange(!enabled) },
                            style = CarButtonStyle.Line,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    topic?.let { TodayTopic(topic = it, onNextTopic = onNextTopic) }
                }
            }
            StartCard(
                status = status,
                mode = mode,
                onStart = onStart,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onShowBlockedNotice = { showBlockedNotice = !onShowBlockedNotice() },
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
                icon = NavigationIcon.Home,
                onClick = onBack,
                style = CarButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier
    )

    if (showBlockedNotice) {
        AlertDialog(
            onDismissRequest = { showBlockedNotice = false },
            title = { Text(text = READY_STATUS_BLOCKED) },
            text = { Text(text = BLOCKED_NOTICE_MESSAGE) },
            confirmButton = {
                TextButton(onClick = { showBlockedNotice = false }) { Text(text = CONFIRM_LABEL) }
            }
        )
    }
}

@Composable
private fun GuideRow(@DrawableRes icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(GuideIconSize).testTag(READY_GUIDE_ICON_TAG)
        )
        KeepWordsText(text = text, style = MaterialTheme.typography.titleLarge)
    }
}

/** 자유그림 오늘의 주제. 주제를 크게 보이고 다른 주제 버튼으로 바꾼다. */
@Composable
private fun TodayTopic(topic: String, onNextTopic: () -> Unit) {
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = TODAY_TOPIC_LABEL,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = topic,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(TODAY_TOPIC_TAG)
    )
    CarButton(
        text = NEXT_TOPIC_LABEL,
        onClick = onNextTopic,
        style = CarButtonStyle.Line,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 상태 안내를 시작 버튼 바로 위에 크게 보이고, 상태를 해결하는 버튼이 있으면 시작 버튼 아래에 둔다. */
@Composable
private fun StartCard(
    status: ChallengeReadyStatus,
    mode: DrawingMode,
    onStart: () -> Unit,
    onRequestPermission: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onShowBlockedNotice: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    OverlayCard(modifier = modifier) {
        KeepWordsText(
            text = status.messageFor(mode),
            style = MaterialTheme.typography.headlineSmall,
            color = if (status.canStart) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        val action: Pair<String, () -> Unit>? = when (status) {
            is ChallengeReadyStatus.PermissionRequired -> if (status.permanentlyDenied) {
                OPEN_SETTINGS_LABEL to onOpenSettings
            } else {
                REQUEST_PERMISSION_LABEL to { onRequestPermission(status.permissions) }
            }
            ChallengeReadyStatus.Blocked -> SHOW_BLOCKED_NOTICE_LABEL to onShowBlockedNotice
            is ChallengeReadyStatus.Error -> RETRY_LABEL to onRetry
            else -> null
        }
        CarButton(
            text = START_LABEL,
            icon = NavigationIcon.Start,
            onClick = onStart,
            enabled = status.canStart,
            modifier = Modifier.fillMaxWidth().height(START_BUTTON_HEIGHT)
        )
        if (action != null) {
            CarButton(
                text = action.first,
                onClick = action.second,
                style = CarButtonStyle.Line,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 준비 화면 제목 */
fun readyTitleOf(mode: DrawingMode): String = when (mode) {
    DrawingMode.CIRCLE -> CIRCLE_READY_TITLE
    DrawingMode.FREE_DRAWING -> FREE_DRAWING_READY_TITLE
    DrawingMode.TERRITORY -> TERRITORY_READY_TITLE
}

private fun ChallengeReadyStatus.messageFor(mode: DrawingMode): String = when (this) {
    ChallengeReadyStatus.Ready -> READY_STATUS_READY
    ChallengeReadyStatus.LocatingPosition -> READY_STATUS_LOCATING
    ChallengeReadyStatus.WaitingForStop -> READY_STATUS_WAITING_FOR_STOP
    ChallengeReadyStatus.Unsupported -> when (mode) {
        DrawingMode.CIRCLE -> READY_STATUS_UNSUPPORTED
        DrawingMode.FREE_DRAWING -> FREE_DRAWING_STATUS_UNSUPPORTED
        DrawingMode.TERRITORY -> TERRITORY_STATUS_UNSUPPORTED
    }
    is ChallengeReadyStatus.PermissionRequired -> if (permanentlyDenied) {
        READY_STATUS_PERMISSION_IN_SETTINGS
    } else {
        READY_STATUS_PERMISSION_REQUIRED
    }
    ChallengeReadyStatus.Blocked -> READY_STATUS_BLOCKED
    is ChallengeReadyStatus.Error -> READY_STATUS_ERROR
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
    // 차량에 따라 앱 정보 화면이 없을 수 있어 일반 설정 화면으로 대신 연다.
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "설정 화면을 열 수 없습니다.", e)
        }
    }
}
