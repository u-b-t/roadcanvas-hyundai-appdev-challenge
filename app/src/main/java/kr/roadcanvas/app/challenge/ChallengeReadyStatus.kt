package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability

/** 동그라미 챌린지를 시작할 수 있는지와 시작할 수 없다면 그 이유. */
sealed interface ChallengeReadyStatus {
    /** 모든 조건이 충족되어 시작할 수 있다. */
    data object Ready : ChallengeReadyStatus

    /** 위치를 쓸 수 있지만 아직 현재 위치를 받지 못했다. */
    data object LocatingPosition : ChallengeReadyStatus

    /** 주행 중이거나 주행 규제를 확인할 수 없어 정차 후 시작해야 한다. */
    data object WaitingForStop : ChallengeReadyStatus

    /** 이 차량에서는 위치 기능을 쓸 수 없다. */
    data object Unsupported : ChallengeReadyStatus

    /**
     * [permissions]를 허용받아야 한다. [permanentlyDenied]이면 "다시 묻지 않음"으로 거절되어
     * 권한 요청 창이 뜨지 않으므로 설정에서 허용해야 한다.
     */
    data class PermissionRequired(val permissions: List<String>, val permanentlyDenied: Boolean) :
        ChallengeReadyStatus

    /** 차량 설정에서 위치 접근이 차단되었다. */
    data object Blocked : ChallengeReadyStatus

    /** 위치를 받는 중 오류가 발생했다. */
    data class Error(val message: String?) : ChallengeReadyStatus

    val canStart: Boolean get() = this == Ready
}

/**
 * 위치 상태와 주행 규제로 시작 조건을 판정한다.
 * 위치 문제는 정차 여부와 상관없이 먼저 해결해야 하므로 주행 규제보다 먼저 안내한다.
 */
fun challengeReadyStatusOf(
    location: LocationAvailability,
    restriction: DrivingRestriction,
    hasCurrentLocation: Boolean,
    permissionPermanentlyDenied: Boolean
): ChallengeReadyStatus = when (location) {
    LocationAvailability.Unsupported -> ChallengeReadyStatus.Unsupported
    is LocationAvailability.PermissionRequired -> ChallengeReadyStatus.PermissionRequired(
        permissions = location.permissions,
        permanentlyDenied = permissionPermanentlyDenied
    )
    LocationAvailability.Blocked -> ChallengeReadyStatus.Blocked
    is LocationAvailability.Error -> ChallengeReadyStatus.Error(location.message)
    LocationAvailability.Available -> when {
        !restriction.allowsInteraction -> ChallengeReadyStatus.WaitingForStop
        !hasCurrentLocation -> ChallengeReadyStatus.LocatingPosition
        else -> ChallengeReadyStatus.Ready
    }
}

/**
 * 권한 요청 결과가 "다시 묻지 않음" 거절인지 판정한다.
 * 거절된 뒤에도 설명을 보여줄 필요가 없다고 하면 시스템이 더 이상 요청 창을 띄우지 않는 상태다.
 */
fun isPermissionPermanentlyDenied(granted: Boolean, shouldShowRationale: Boolean): Boolean =
    !granted && !shouldShowRationale
