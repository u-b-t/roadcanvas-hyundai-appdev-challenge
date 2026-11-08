package kr.roadcanvas.app.challenge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kr.roadcanvas.app.vehicle.DrivingRestrictionProvider
import kr.roadcanvas.app.vehicle.LocationProvider

/**
 * 준비 화면의 시작 조건을 구독한다. 화면이 보이는 동안에만 위치와 주행 규제를 구독하고,
 * 화면을 떠나 구독자가 없어지면 바로 구독을 해제한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeReadyViewModel(
    private val locationProvider: LocationProvider,
    drivingRestrictionProvider: DrivingRestrictionProvider
) : ViewModel() {
    private val locationRequest = MutableStateFlow(0)
    private val permissionPermanentlyDenied = MutableStateFlow(false)

    // 위치 수신을 다시 시작하면 구현이 지원 여부와 권한을 다시 확인한다.
    private val hasCurrentLocation = locationRequest.flatMapLatest {
        locationProvider.locations()
            .map { true }
            .onStart { emit(false) }
            .catch { emit(false) }
    }.distinctUntilChanged()

    val status: StateFlow<ChallengeReadyStatus> = combine(
        locationProvider.availability,
        drivingRestrictionProvider.restriction,
        hasCurrentLocation,
        permissionPermanentlyDenied,
        ::challengeReadyStatusOf
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = challengeReadyStatusOf(
            location = locationProvider.availability.value,
            restriction = drivingRestrictionProvider.restriction.value,
            hasCurrentLocation = false,
            permissionPermanentlyDenied = false
        )
    )

    /** 위치 수신을 처음부터 다시 요청한다. */
    fun retry() {
        locationRequest.update { it + 1 }
    }

    /** 차량의 위치 접근 차단 안내를 띄운다. 띄우지 못했으면 false를 돌려준다. */
    fun showAccessBlockedNotice(activityContext: Context): Boolean =
        locationProvider.showAccessBlockedNotice(activityContext)

    /** 권한 요청 결과를 반영하고 허용 여부를 다시 확인한다. */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        permissionPermanentlyDenied.value =
            isPermissionPermanentlyDenied(granted, shouldShowRationale)
        retry()
    }
}
