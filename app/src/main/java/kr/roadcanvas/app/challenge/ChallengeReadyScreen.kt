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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.map.RouteMap
import kr.roadcanvas.app.map.RouteMapOptions
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.ui.components.CarButton
import kr.roadcanvas.app.ui.components.CarButtonStyle
import kr.roadcanvas.app.ui.components.DriverSideOverlay
import kr.roadcanvas.app.ui.components.OverlayCard
import kr.roadcanvas.app.ui.components.driverSideOverlayContentPadding

const val CIRCLE_READY_TITLE = "동그라미 챌린지 준비"
const val CHALLENGE_GOAL_HEADING = "목표"
const val CHALLENGE_GOAL = "원에 가깝게 주행한 뒤 출발점 근처로 돌아오세요"
const val CHALLENGE_RULES_HEADING = "진행 방법"
const val CHALLENGE_RULE_START_STOP = "시작과 종료는 정차한 뒤 버튼으로 해요"
const val CHALLENGE_RULE_NO_PAUSE = "도중에 일시정지할 수 없어요"
const val CHALLENGE_RULE_GIVE_UP = "정차 중에는 언제든 포기할 수 있어요"

const val READY_STATUS_READY = "시작할 준비가 되었어요"
const val READY_STATUS_LOCATING = "위치 확인 중"
const val READY_STATUS_WAITING_FOR_STOP = "정차한 뒤 시작할 수 있어요"
const val READY_STATUS_UNSUPPORTED = "이 차량에서는 동그라미 챌린지를 사용할 수 없어요"
const val READY_STATUS_PERMISSION_REQUIRED = "위치 권한이 필요해요"
const val READY_STATUS_PERMISSION_IN_SETTINGS = "설정에서 위치 권한을 허용해 주세요"
const val READY_STATUS_BLOCKED = "위치 접근이 차단되어 있어요"
const val READY_STATUS_ERROR = "위치를 확인하지 못했어요"
const val BLOCKED_NOTICE_MESSAGE = "차량 설정에서 위치 접근을 허용한 뒤 다시 시도해 주세요"

const val START_LABEL = "시작"
const val BACK_TO_MODE_SELECT_LABEL = "모드 선택으로"
const val REQUEST_PERMISSION_LABEL = "권한 요청"
const val OPEN_SETTINGS_LABEL = "설정 열기"
const val SHOW_BLOCKED_NOTICE_LABEL = "차단 안내 보기"
const val RETRY_LABEL = "다시 시도"
private const val CONFIRM_LABEL = "확인"
private const val TAG = "ChallengeReady"

/** 준비 화면에 시작 조건 구독과 권한 요청을 연결한다. */
@Composable
fun ChallengeReadyRoute(onStart: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: ChallengeReadyViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val container = context.appContainer
                ChallengeReadyViewModel(
                    container.locationProvider,
                    container.drivingRestrictionProvider
                )
            }
        }
    )
    // 화면이 멈추면 구독자가 없어져 위치와 주행 규제 구독도 해제된다.
    val status by viewModel.status.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

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
        onStart = onStart,
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
        currentLocation = currentLocation?.let { RoutePoint(it.latitude, it.longitude) }
    )
}

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
    onShowBlockedNotice: () -> Boolean = { false }
) {
    // 차량의 차단 안내를 띄우지 못하면 앱 안에서 같은 내용을 안내한다.
    var showBlockedNotice by rememberSaveable { mutableStateOf(false) }

    DriverSideOverlay(
        background = {
            RouteMap(
                points = listOfNotNull(currentLocation),
                options = RouteMapOptions(
                    showCurrentLocation = true,
                    contentPadding = driverSideOverlayContentPadding()
                ),
                modifier = Modifier.fillMaxSize()
            )
        },
        controls = {
            // 내용이 화면 높이를 넘으면 안내 카드만 스크롤하고 상태와 버튼은 항상 보이게 둔다.
            OverlayCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = CIRCLE_READY_TITLE, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = CHALLENGE_GOAL_HEADING,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = CHALLENGE_GOAL, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = CHALLENGE_RULES_HEADING,
                        style = MaterialTheme.typography.titleMedium
                    )
                    listOf(
                        CHALLENGE_RULE_START_STOP,
                        CHALLENGE_RULE_NO_PAUSE,
                        CHALLENGE_RULE_GIVE_UP
                    ).forEach { rule ->
                        Text(text = rule, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            StatusCard(
                status = status,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onShowBlockedNotice = { showBlockedNotice = !onShowBlockedNotice() },
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = START_LABEL,
                onClick = onStart,
                enabled = status.canStart,
                modifier = Modifier.fillMaxWidth()
            )
            CarButton(
                text = BACK_TO_MODE_SELECT_LABEL,
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
private fun StatusCard(
    status: ChallengeReadyStatus,
    onRequestPermission: (List<String>) -> Unit,
    onOpenSettings: () -> Unit,
    onShowBlockedNotice: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    OverlayCard(modifier = modifier) {
        Text(
            text = status.message,
            style = MaterialTheme.typography.titleLarge,
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

private val ChallengeReadyStatus.message: String
    get() = when (this) {
        ChallengeReadyStatus.Ready -> READY_STATUS_READY
        ChallengeReadyStatus.LocatingPosition -> READY_STATUS_LOCATING
        ChallengeReadyStatus.WaitingForStop -> READY_STATUS_WAITING_FOR_STOP
        ChallengeReadyStatus.Unsupported -> READY_STATUS_UNSUPPORTED
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
