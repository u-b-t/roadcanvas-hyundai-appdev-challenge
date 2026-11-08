package kr.roadcanvas.app.challenge

import kr.roadcanvas.app.vehicle.DrivingRestriction
import kr.roadcanvas.app.vehicle.LocationAvailability
import kr.roadcanvas.app.vehicle.VehiclePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeReadyStatusTest {
    private val permissionRequired =
        LocationAvailability.PermissionRequired(listOf(VehiclePermissions.FUSED_LOCATION))

    private fun statusOf(
        location: LocationAvailability = LocationAvailability.Available,
        restriction: DrivingRestriction = DrivingRestriction.UNRESTRICTED,
        hasCurrentLocation: Boolean = true,
        permanentlyDenied: Boolean = false
    ) = challengeReadyStatusOf(location, restriction, hasCurrentLocation, permanentlyDenied)

    @Test
    fun `위치를 받았고 조작 가능하면 시작할 수 있다`() {
        val status = statusOf()

        assertEquals(ChallengeReadyStatus.Ready, status)
        assertTrue(status.canStart)
    }

    @Test
    fun `위치를 쓸 수 있어도 현재 위치를 받기 전에는 위치 확인 중이다`() {
        val status = statusOf(hasCurrentLocation = false)

        assertEquals(ChallengeReadyStatus.LocatingPosition, status)
        assertFalse(status.canStart)
    }

    @Test
    fun `조작 제한이나 확인 불가면 정차 후 시작해야 한다`() {
        listOf(DrivingRestriction.RESTRICTED, DrivingRestriction.UNKNOWN).forEach { restriction ->
            listOf(true, false).forEach { hasLocation ->
                val status = statusOf(restriction = restriction, hasCurrentLocation = hasLocation)

                assertEquals(ChallengeReadyStatus.WaitingForStop, status)
                assertFalse(status.canStart)
            }
        }
    }

    @Test
    fun `위치를 쓸 수 없으면 주행 규제와 상관없이 위치 상태를 안내한다`() {
        val expected = mapOf(
            LocationAvailability.Unsupported to ChallengeReadyStatus.Unsupported,
            permissionRequired to ChallengeReadyStatus.PermissionRequired(
                listOf(VehiclePermissions.FUSED_LOCATION),
                permanentlyDenied = false
            ),
            LocationAvailability.Blocked to ChallengeReadyStatus.Blocked,
            LocationAvailability.Error("실패") to ChallengeReadyStatus.Error("실패")
        )
        expected.forEach { (location, status) ->
            DrivingRestriction.entries.forEach { restriction ->
                val actual = statusOf(location = location, restriction = restriction)

                assertEquals(status, actual)
                assertFalse(actual.canStart)
            }
        }
    }

    @Test
    fun `다시 묻지 않음으로 거절되었으면 권한 필요 상태에 함께 알린다`() {
        val status = statusOf(location = permissionRequired, permanentlyDenied = true)

        assertEquals(
            ChallengeReadyStatus.PermissionRequired(
                listOf(VehiclePermissions.FUSED_LOCATION),
                permanentlyDenied = true
            ),
            status
        )
    }

    @Test
    fun `권한이 허용되면 다시 묻지 않음 표시를 무시한다`() {
        assertEquals(ChallengeReadyStatus.Ready, statusOf(permanentlyDenied = true))
    }

    @Test
    fun `거절 후 설명이 필요 없다고 하면 다시 묻지 않음이다`() {
        assertTrue(isPermissionPermanentlyDenied(granted = false, shouldShowRationale = false))
        assertFalse(isPermissionPermanentlyDenied(granted = false, shouldShowRationale = true))
        assertFalse(isPermissionPermanentlyDenied(granted = true, shouldShowRationale = false))
    }
}
