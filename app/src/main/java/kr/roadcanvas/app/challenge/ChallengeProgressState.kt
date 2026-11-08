package kr.roadcanvas.app.challenge

import java.util.Locale
import kotlin.math.roundToLong
import kr.roadcanvas.app.drawing.RoutePoint
import kr.roadcanvas.app.recording.RecordingState
import kr.roadcanvas.app.vehicle.DrivingRestriction

/** 진행 화면이 보여주는 기록 단계 */
enum class ChallengeProgressPhase {
    /** 기록을 시작하거나 저장된 기록을 불러오는 중 */
    PREPARING,
    RECORDING,

    /** 종료해 결과 화면으로 넘어가는 중 */
    FINISHED,
    FAILED
}

/** 진행 화면 상태. [path]와 [elapsedMillis]는 화면 갱신 시점의 값이다. */
data class ChallengeProgressUiState(
    val phase: ChallengeProgressPhase,
    val elapsedMillis: Long,
    val distanceMeters: Double,
    val path: List<RoutePoint>,
    val restriction: DrivingRestriction
) {
    /** 정차해 조작할 수 있을 때만 종료·포기할 수 있다. */
    val canControl: Boolean
        get() = phase == ChallengeProgressPhase.RECORDING && restriction.allowsInteraction

    /** 종료·포기를 막은 이유. 기록 중이 아니거나 조작할 수 있으면 null이다. */
    val controlBlockedReason: String?
        get() = if (phase != ChallengeProgressPhase.RECORDING) {
            null
        } else {
            when (restriction) {
                DrivingRestriction.UNRESTRICTED -> null
                DrivingRestriction.RESTRICTED -> CONTROL_RESTRICTED_REASON
                DrivingRestriction.UNKNOWN -> CONTROL_UNKNOWN_REASON
            }
        }
}

/** 기록 상태와 주행 규제를 [nowMillis] 시점의 화면 상태로 바꾼다. */
fun challengeProgressOf(
    recording: RecordingState,
    restriction: DrivingRestriction,
    nowMillis: Long
): ChallengeProgressUiState = when (recording) {
    RecordingState.Idle -> ChallengeProgressUiState(
        ChallengeProgressPhase.PREPARING,
        0L,
        0.0,
        emptyList(),
        restriction
    )
    is RecordingState.Recording -> ChallengeProgressUiState(
        phase = ChallengeProgressPhase.RECORDING,
        elapsedMillis = (nowMillis - recording.startedAtMillis).coerceAtLeast(0L),
        distanceMeters = recording.distanceMeters,
        path = recording.path,
        restriction = restriction
    )
    is RecordingState.Finished -> ChallengeProgressUiState(
        phase = ChallengeProgressPhase.FINISHED,
        elapsedMillis = recording.durationMillis,
        distanceMeters = recording.distanceMeters,
        path = recording.path,
        restriction = restriction
    )
    is RecordingState.Failed -> ChallengeProgressUiState(
        ChallengeProgressPhase.FAILED,
        0L,
        0.0,
        emptyList(),
        restriction
    )
}

/** 경과 시간을 `분:초`, 한 시간이 넘으면 `시:분:초`로 표시한다. */
fun formatElapsed(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** 이동 거리를 1km 미만은 m 단위 정수로, 그 이상은 km 단위 소수 둘째 자리까지 표시한다. */
fun formatDistance(meters: Double): String {
    val rounded = meters.coerceAtLeast(0.0).roundToLong()
    return if (rounded < 1_000) {
        "$rounded m"
    } else {
        String.format(Locale.ROOT, "%.2f km", meters / 1_000)
    }
}
