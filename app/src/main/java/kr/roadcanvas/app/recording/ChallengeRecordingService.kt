package kr.roadcanvas.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kr.roadcanvas.app.MainActivity
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.drawing.DrawingMode

private const val TAG = "ChallengeRecording"
private const val CHANNEL_ID = "challenge_recording"
private const val NOTIFICATION_ID = 1
private const val EXTRA_MODE = "mode"

const val RECORDING_CHANNEL_NAME = "도전 기록"
const val RECORDING_NOTIFICATION_TITLE = "동그라미 챌린지 기록 중"
const val FREE_DRAWING_NOTIFICATION_TITLE = "자유그림 기록 중"
const val RESUMED_NOTIFICATION_TITLE = "주행 경로 기록 중"
const val RECORDING_NOTIFICATION_TEXT = "정차한 뒤 앱에서 종료하거나 포기할 수 있어요"

/**
 * 주행 중 앱 화면이 차단되어도 기록이 이어지도록 위치 타입 포그라운드 서비스에서 [ChallengeRecorder]를 실행한다.
 * 시스템이 프로세스를 다시 만들어 서비스를 재시작하면 저장된 진행 중 기록을 이어서 기록한다.
 */
class ChallengeRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null

    override fun onCreate() {
        super.onCreate()
        // 화면이 차단된 동안에도 기록이 이어지는지 logcat으로 확인할 수 있도록 점 수를 남긴다.
        scope.launch {
            appContainer.challengeRecorder.state
                .filterIsInstance<RecordingState.Recording>()
                .map { it.path.size }
                .distinctUntilChanged()
                .collect { Log.i(TAG, "경로 점 ${it}개") }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = appContainer.challengeRecorder
        val newMode = intent?.getStringExtra(EXTRA_MODE)?.let(DrawingMode::fromKey)
        try {
            startInForeground(newMode)
        } catch (e: Exception) {
            // Android 위치 권한이 없거나 백그라운드에서 시작하면 포그라운드 서비스를 시작할 수 없다.
            Log.e(TAG, "기록 서비스를 포그라운드로 시작하지 못했습니다.", e)
            recorder.fail(e, newMode)
            stopSelf()
            return START_NOT_STICKY
        }
        if (recording?.isActive != true) {
            val request = newMode?.let(RecordingRequest::New) ?: RecordingRequest.Resume
            Log.i(TAG, "기록 시작: $request")
            recording = scope.launch {
                recorder.run(request)
                Log.i(TAG, "기록 종료: ${recorder.state.value::class.simpleName}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(mode: DrawingMode?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                RECORDING_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(mode),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
    }

    /** 이어서 기록할 때는 서비스를 시작하는 시점에 모드를 모르므로 모드 없는 제목을 쓴다. */
    private fun notification(mode: DrawingMode?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(
                when (mode) {
                    DrawingMode.CIRCLE -> RECORDING_NOTIFICATION_TITLE
                    DrawingMode.FREE_DRAWING -> FREE_DRAWING_NOTIFICATION_TITLE
                    null -> RESUMED_NOTIFICATION_TITLE
                }
            )
            .setContentText(RECORDING_NOTIFICATION_TEXT)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        fun intent(context: Context, request: RecordingRequest): Intent =
            Intent(context, ChallengeRecordingService::class.java).apply {
                if (request is RecordingRequest.New) putExtra(EXTRA_MODE, request.mode.key)
            }
    }
}

/** [ChallengeRecordingService]를 시작하고 멈춘다. */
class ServiceRecordingRunner(private val context: Context) : RecordingRunner {
    override fun run(request: RecordingRequest) {
        ContextCompat.startForegroundService(
            context,
            ChallengeRecordingService.intent(context, request)
        )
    }

    override fun stop() {
        context.stopService(Intent(context, ChallengeRecordingService::class.java))
    }
}
