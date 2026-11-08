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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.ui.theme.MIN_TOUCH_TARGET

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
        modifier = modifier
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
    onShowBlockedNotice: () -> Boolean = { false }
) {
    // 차량의 차단 안내를 띄우지 못하면 앱 안에서 같은 내용을 안내한다.
    var showBlockedNotice by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text(text = CIRCLE_READY_TITLE, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = CHALLENGE_GOAL_HEADING, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = CHALLENGE_GOAL, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(32.dp))
            Text(text = CHALLENGE_RULES_HEADING, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            listOf(CHALLENGE_RULE_START_STOP, CHALLENGE_RULE_NO_PAUSE, CHALLENGE_RULE_GIVE_UP)
                .forEach { rule ->
                    Text(
                        text = rule,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(
                status = status,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings,
                onShowBlockedNotice = { showBlockedNotice = !onShowBlockedNotice() },
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Button(
                onClick = onStart,
                enabled = status.canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(text = START_LABEL, style = MaterialTheme.typography.headlineSmall)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
            ) {
                Text(text = BACK_TO_MODE_SELECT_LABEL, style = MaterialTheme.typography.titleLarge)
            }
        }
    }

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
    val containerColor = if (status.canStart) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(
                16.dp,
                alignment = Alignment.CenterVertically
            )
        ) {
            Text(text = status.message, style = MaterialTheme.typography.headlineSmall)
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
                Button(
                    onClick = action.second,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET)
                ) {
                    Text(text = action.first, style = MaterialTheme.typography.titleLarge)
                }
            }
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
