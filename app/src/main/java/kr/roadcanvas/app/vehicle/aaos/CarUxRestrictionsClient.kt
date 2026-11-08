package kr.roadcanvas.app.vehicle.aaos

/** AAOS `CarUxRestrictionsManager` 연결을 감싼다. 테스트에서는 대체 구현을 쓴다. */
interface CarUxRestrictionsClient {
    /**
     * Car 서비스에 연결하고 주행 규제 리스너를 등록한다.
     * 반환값을 닫으면 리스너를 해제하고 Car 연결을 끊는다.
     */
    fun connect(listener: Listener): AutoCloseable

    interface Listener {
        /** 등록 직후의 현재 값과 이후 바뀐 값을 `isRequiresDistractionOptimization`으로 전달한다. */
        fun onUxRestrictionsChanged(requiresDistractionOptimization: Boolean)

        /** Car 서비스 연결이 끊기거나 값을 읽지 못했다. */
        fun onUnavailable()
    }
}

/** Car API가 없는 환경에서 쓰는 구현. 항상 값을 확인할 수 없다고 알린다. */
object UnavailableCarUxRestrictionsClient : CarUxRestrictionsClient {
    override fun connect(listener: CarUxRestrictionsClient.Listener): AutoCloseable {
        listener.onUnavailable()
        return AutoCloseable {}
    }
}
