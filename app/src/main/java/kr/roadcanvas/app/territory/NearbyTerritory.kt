package kr.roadcanvas.app.territory

import kotlin.math.PI
import kotlin.math.cos
import kr.roadcanvas.app.account.defaultNickname
import kr.roadcanvas.app.score.GeoPoint

/** 준비 화면에서 주변 칸을 읽는 범위. 현재 위치에서 동서남북으로 이 거리(m)까지다. */
const val NEARBY_TERRITORY_RADIUS_METERS = 600.0

/** 현재 위치가 마지막으로 읽은 위치에서 이 거리(m) 넘게 움직이면 주변 칸을 다시 읽는다. */
const val NEARBY_TERRITORY_RELOAD_METERS = 300.0

const val NEARBY_TERRITORY_LOADING = "주변 땅을 불러오는 중"
const val NEARBY_TERRITORY_EMPTY = "주변에 주인 있는 칸이 없어요. 첫 땅을 차지해 보세요"
const val NEARBY_TERRITORY_TOO_MANY = "주변 칸이 너무 많아 요약하지 못했어요"
const val NEARBY_TERRITORY_FAILED = "주변 땅을 불러오지 못했어요"

private const val METERS_PER_DEGREE_LATITUDE = 6_371_000.0 * PI / 180

/** 주변에서 칸을 가장 많이 가진 사용자. [mine]이면 나다. */
data class NearbyLeader(val nickname: String, val cellCount: Int, val mine: Boolean)

/** 주변 칸 요약. [totalCells]는 주인 있는 칸 수, [myCells]는 그중 내 칸 수다. */
data class NearbyTerritorySummary(val totalCells: Int, val myCells: Int, val leader: NearbyLeader?)

/** 준비 화면의 주변 영토 상태 */
sealed interface NearbyTerritory {
    data object Loading : NearbyTerritory

    data class Loaded(val cells: List<TerritoryCell>, val summary: NearbyTerritorySummary) :
        NearbyTerritory

    /** 칸이 많아 지도 조회 한도를 넘었다. */
    data object TooMany : NearbyTerritory

    data class Failed(val reason: TerritoryFailure) : NearbyTerritory
}

/**
 * 주변 [cells]를 내 칸과 다른 사용자 칸으로 세고, 칸이 가장 많은 사용자를 고른다.
 * 칸 수가 같으면 UID 순서로 앞선 사용자다. 닉네임은 [ranking]에서 찾고, 없으면 자동 닉네임이다.
 */
fun nearbyTerritorySummaryOf(
    cells: List<TerritoryCell>,
    ranking: List<TerritoryRank>
): NearbyTerritorySummary {
    val leader = cells.groupBy { it.ownerUid }
        .map { (uid, owned) -> Triple(uid, owned.size, owned.first().mine) }
        .sortedWith(
            compareByDescending<Triple<String, Int, Boolean>> {
                it.second
            }.thenBy { it.first }
        )
        .firstOrNull()
        ?.let { (uid, count, mine) ->
            val nickname = ranking.firstOrNull { it.uid == uid }?.nickname ?: defaultNickname(uid)
            NearbyLeader(nickname, count, mine)
        }
    return NearbyTerritorySummary(cells.size, cells.count { it.mine }, leader)
}

/** 예: `주변 칸 42개 중 내 땅 3칸, 가장 많은 사람 드라이버-01님 18칸` */
fun formatNearbySummary(summary: NearbyTerritorySummary): String {
    if (summary.totalCells == 0) return NEARBY_TERRITORY_EMPTY
    val base = "주변 칸 ${summary.totalCells}개 중 내 땅 ${summary.myCells}칸"
    val leader = summary.leader ?: return base
    return if (leader.mine) {
        "$base, 내가 가장 많아요"
    } else {
        "$base, 가장 많은 사람 ${leader.nickname}님 ${leader.cellCount}칸"
    }
}

/** 화면에 보일 주변 영토 안내 */
val NearbyTerritory.text: String
    get() = when (this) {
        NearbyTerritory.Loading -> NEARBY_TERRITORY_LOADING
        is NearbyTerritory.Loaded -> formatNearbySummary(summary)
        NearbyTerritory.TooMany -> NEARBY_TERRITORY_TOO_MANY
        is NearbyTerritory.Failed -> "$NEARBY_TERRITORY_FAILED (${reason.text})"
    }

/** [center]에서 동서남북으로 [radiusMeters]까지의 범위 */
fun boundsAround(
    center: GeoPoint,
    radiusMeters: Double = NEARBY_TERRITORY_RADIUS_METERS
): GeoBounds {
    val latitudeDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE
    val longitudeDelta =
        radiusMeters / (METERS_PER_DEGREE_LATITUDE * cos(center.latitude * PI / 180))
    return GeoBounds(
        south = center.latitude - latitudeDelta,
        west = center.longitude - longitudeDelta,
        north = center.latitude + latitudeDelta,
        east = center.longitude + longitudeDelta
    )
}

/** [center] 주변 칸과 요약을 읽는다. 순위를 읽지 못해도 칸 요약은 자동 닉네임으로 보여준다. */
suspend fun TerritoryGame.nearbyTerritory(center: GeoPoint): NearbyTerritory {
    val cells = when (val found = mapCells(boundsAround(center))) {
        is TerritoryCall.Failure -> return NearbyTerritory.Failed(found.reason)
        is TerritoryCall.Success -> found.value
    }
    val loaded = cells as? TerritoryMapCells.Loaded ?: return NearbyTerritory.TooMany
    val ranking = (ranking() as? TerritoryCall.Success)?.value.orEmpty()
    return NearbyTerritory.Loaded(loaded.cells, nearbyTerritorySummaryOf(loaded.cells, ranking))
}
