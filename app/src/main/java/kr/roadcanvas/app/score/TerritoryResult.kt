package kr.roadcanvas.app.score

/**
 * 땅따먹기 판정 결과. [TerritoryCalculator.calculate]가 기록한 경로에서 만든다.
 * 화면은 `when`으로 네 경우를 나눠 보여준다. 영역을 차지한 경우는 [Claimed]뿐이다.
 */
sealed interface TerritoryResult {

    /**
     * 경로가 닫혀 최소 면적 이상의 영역을 차지했다.
     *
     * @property areaSquareMeters 영역 면적(m²)
     * @property boundary 영역 경계 다각형의 꼭짓점. 경로를 따라가는 순서이며 첫 점을 끝에 반복하지 않는다.
     *   지도에 그릴 때 마지막 점과 첫 점을 이어 닫는다. 자기 교차가 없는 단순 다각형이다.
     * @property perimeterMeters [boundary] 둘레(m). 닫는 변을 포함한다. 주행 거리와 다를 수 있다.
     */
    data class Claimed(
        val areaSquareMeters: Double,
        val boundary: List<GeoPoint>,
        val perimeterMeters: Double
    ) : TerritoryResult

    /**
     * 끝점이 시작점에서 [toleranceMeters]보다 멀어 경로가 닫히지 않았다.
     *
     * @property gapMeters 시작점과 끝점 사이 직선 거리(m)
     * @property toleranceMeters 닫힌 경로로 보는 최대 거리(m). 안내 문구에 쓴다.
     */
    data class NotClosed(val gapMeters: Double, val toleranceMeters: Double) : TerritoryResult

    /**
     * 경로는 닫혔지만 영역이 [minAreaSquareMeters]보다 작다. 직선 왕복처럼 둘러싼 영역이 없으면 면적은 0에 가깝다.
     *
     * @property areaSquareMeters 판정한 영역 면적(m²)
     * @property minAreaSquareMeters 차지할 수 있는 최소 면적(m²). 안내 문구에 쓴다.
     */
    data class TooSmall(val areaSquareMeters: Double, val minAreaSquareMeters: Double) :
        TerritoryResult

    /** 유효한 위치값이 부족하거나 서로 다른 지점이 셋 미만이라 판정할 수 없다. 정지한 채 기록한 경우도 여기에 해당한다. */
    data object InsufficientPoints : TerritoryResult
}
