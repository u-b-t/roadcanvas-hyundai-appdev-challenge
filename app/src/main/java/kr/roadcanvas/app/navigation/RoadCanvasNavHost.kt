package kr.roadcanvas.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import kr.roadcanvas.app.appContainer
import kr.roadcanvas.app.challenge.ChallengeProgressRoute
import kr.roadcanvas.app.challenge.ChallengeReadyRoute
import kr.roadcanvas.app.challenge.ChallengeResultRoute
import kr.roadcanvas.app.challenge.rememberChallengeStarter
import kr.roadcanvas.app.circleranking.CircleRankingRoute
import kr.roadcanvas.app.drawing.DrawingMode
import kr.roadcanvas.app.gallery.PublicArtworkDetailRoute
import kr.roadcanvas.app.gallery.PublicGalleryRoute
import kr.roadcanvas.app.map.RoutePoint
import kr.roadcanvas.app.modeselect.HomeScreen
import kr.roadcanvas.app.modeselect.rememberModeRecordSummary
import kr.roadcanvas.app.mydrawings.MyDrawingsRoute
import kr.roadcanvas.app.splash.SPLASH_DURATION_MILLIS
import kr.roadcanvas.app.splash.SplashScreen
import kr.roadcanvas.app.territorymap.TerritoryMapRoute
import kr.roadcanvas.app.vehicle.DrivingRestriction

object Routes {
    const val SPLASH = "splash"
    const val MODE_SELECT = "modeSelect"

    const val CIRCLE_READY = "circleReady"
    const val FREE_READY = "freeReady"
    const val TERRITORY_READY = "territoryReady"

    /**
     * 모든 모드가 함께 쓰는 진행 화면. 주행 중에도 보여주는 화면이 하나로 유지되도록 경로를 나누지 않는다.
     * [ARG_MODE]는 시작한 모드이며, 없으면 기록 상태의 모드를 쓴다.
     */
    const val ARG_MODE = "mode"
    private const val PROGRESS = "circleProgress"
    const val CIRCLE_PROGRESS = "$PROGRESS?$ARG_MODE={$ARG_MODE}"

    fun progress(mode: DrawingMode? = null) =
        if (mode == null) PROGRESS else "$PROGRESS?$ARG_MODE=${mode.key}"

    /**
     * 종료한 기록은 진행 중 기록 저장소에 그대로 남아 있고, 시작·종료 시각을 인자로 넘긴다.
     * 진행 중 기록은 한 번에 하나뿐이므로 시작 시각으로 그 기록을 확인한다.
     * 결과 화면은 이 기록으로 완료 기록을 저장하는 데 성공한 뒤 진행 중 기록을 지운다.
     */
    const val ARG_STARTED_AT = "startedAtMillis"
    const val ARG_ENDED_AT = "endedAtMillis"
    const val CIRCLE_RESULT = "circleResult/{$ARG_STARTED_AT}/{$ARG_ENDED_AT}"
    const val FREE_RESULT = "freeResult/{$ARG_STARTED_AT}/{$ARG_ENDED_AT}"
    const val TERRITORY_RESULT = "territoryResult/{$ARG_STARTED_AT}/{$ARG_ENDED_AT}"

    fun circleResult(startedAtMillis: Long, endedAtMillis: Long) =
        "circleResult/$startedAtMillis/$endedAtMillis"

    fun freeResult(startedAtMillis: Long, endedAtMillis: Long) =
        "freeResult/$startedAtMillis/$endedAtMillis"

    fun territoryResult(startedAtMillis: Long, endedAtMillis: Long) =
        "territoryResult/$startedAtMillis/$endedAtMillis"

    /** [mode]의 준비 화면 */
    fun ready(mode: DrawingMode) = when (mode) {
        DrawingMode.CIRCLE -> CIRCLE_READY
        DrawingMode.FREE_DRAWING -> FREE_READY
        DrawingMode.TERRITORY -> TERRITORY_READY
    }

    /** [mode]의 결과 화면 경로 형식 */
    fun resultPattern(mode: DrawingMode) = when (mode) {
        DrawingMode.CIRCLE -> CIRCLE_RESULT
        DrawingMode.FREE_DRAWING -> FREE_RESULT
        DrawingMode.TERRITORY -> TERRITORY_RESULT
    }

    /** [mode]의 결과 화면 */
    fun result(mode: DrawingMode, startedAtMillis: Long, endedAtMillis: Long) = when (mode) {
        DrawingMode.CIRCLE -> circleResult(startedAtMillis, endedAtMillis)
        DrawingMode.FREE_DRAWING -> freeResult(startedAtMillis, endedAtMillis)
        DrawingMode.TERRITORY -> territoryResult(startedAtMillis, endedAtMillis)
    }

    const val MY_DRAWINGS = "myDrawings"

    /** 동그라미 챌린지 순위 */
    const val CIRCLE_RANKING = "circleRanking"

    /** 공개 작품 목록과 [ARG_ARTWORK_ID] 작품의 상세 */
    const val GALLERY = "gallery"
    const val ARG_ARTWORK_ID = "artworkId"
    const val GALLERY_ARTWORK = "gallery/{$ARG_ARTWORK_ID}"

    fun galleryArtwork(artworkId: String) = "gallery/${Uri.encode(artworkId)}"

    /** 영토 지도와 순위. [ARG_LATITUDE]·[ARG_LONGITUDE]가 있으면 그 위치에서 지도를 연다. */
    const val ARG_LATITUDE = "lat"
    const val ARG_LONGITUDE = "lon"
    private const val TERRITORY_MAP_BASE = "territoryMap"
    const val TERRITORY_MAP =
        "$TERRITORY_MAP_BASE?$ARG_LATITUDE={$ARG_LATITUDE}&$ARG_LONGITUDE={$ARG_LONGITUDE}"

    fun territoryMap(center: RoutePoint? = null) = if (center == null) {
        TERRITORY_MAP_BASE
    } else {
        "$TERRITORY_MAP_BASE?$ARG_LATITUDE=${center.latitude}&$ARG_LONGITUDE=${center.longitude}"
    }
}

@Composable
fun RoadCanvasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    splashDurationMillis: Long = SPLASH_DURATION_MILLIS
) {
    val container = LocalContext.current.appContainer
    val recorder = container.challengeRecorder
    val restriction = container.drivingRestrictionProvider.restriction
        .collectAsStateWithLifecycle()

    // 모든 화면은 주행 규제에 따라 정차 안내로 가릴지 정하는 screen으로 등록한다.
    fun NavGraphBuilder.screen(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        exitTransition: ScreenExitTransition? = null,
        content: @Composable (NavBackStackEntry) -> Unit
    ) = gatedComposable(route, arguments, restriction, exitTransition, content)
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        // 시작 화면은 사라지는 효과 없이 그대로 두어 모드 선택 화면이 같은 커버 배경 위에서 나타나게 한다.
        // 두 화면을 함께 흐리게 하면 그 사이로 테마 배경이 비쳐 배경이 끊겨 보인다.
        screen(Routes.SPLASH, exitTransition = { ExitTransition.None }) {
            val scope = rememberCoroutineScope()
            SplashScreen(
                durationMillis = splashDurationMillis,
                onFinished = {
                    scope.launch {
                        // 종료했지만 저장하지 못한 도전은 결과 화면이 다시 저장하고,
                        // 진행 중이던 도전은 진행 화면이 기록을 이어간다.
                        val unsaved = recorder.unsavedFinish()
                        val resumeMode = if (unsaved == null) recorder.inProgressMode() else null
                        navController.navigate(Routes.MODE_SELECT) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        when {
                            unsaved != null -> navController.navigate(
                                Routes.result(
                                    unsaved.mode,
                                    unsaved.startedAtMillis,
                                    unsaved.endedAtMillis
                                )
                            )
                            resumeMode != null -> navController.navigate(
                                Routes.progress(resumeMode)
                            )
                        }
                    }
                }
            )
        }
        screen(Routes.MODE_SELECT) {
            HomeScreen(
                onCircleChallengeClick = { navController.navigate(Routes.CIRCLE_READY) },
                onCircleRankingClick = { navController.navigate(Routes.CIRCLE_RANKING) },
                onTerritoryClick = { navController.navigate(Routes.TERRITORY_READY) },
                onTerritoryMapClick = { navController.navigate(Routes.territoryMap()) },
                onFreeDrawingClick = { navController.navigate(Routes.FREE_READY) },
                onGalleryClick = { navController.navigate(Routes.GALLERY) },
                onMyDrawingsClick = { navController.navigate(Routes.MY_DRAWINGS) },
                recordSummary = rememberModeRecordSummary().value
            )
        }
        DrawingMode.entries.forEach { mode ->
            screen(Routes.ready(mode)) {
                val startChallenge = rememberChallengeStarter(
                    mode = mode,
                    onStarted = {
                        navController.navigate(Routes.progress(mode)) {
                            popUpTo(Routes.ready(mode)) { inclusive = true }
                        }
                    }
                )
                ChallengeReadyRoute(
                    mode = mode,
                    onStart = startChallenge,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        screen(
            Routes.CIRCLE_PROGRESS,
            arguments = listOf(
                navArgument(Routes.ARG_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            fun leaveTo(route: String) = navController.navigate(route) {
                popUpTo(Routes.CIRCLE_PROGRESS) { inclusive = true }
            }
            ChallengeProgressRoute(
                mode = entry.arguments?.getString(Routes.ARG_MODE)?.let(DrawingMode::fromKey),
                onFinished = {
                    leaveTo(Routes.result(it.mode, it.startedAtMillis, it.endedAtMillis))
                },
                onGaveUp = { leaveTo(Routes.ready(it)) },
                onBackToReady = { leaveTo(Routes.ready(it)) }
            )
        }
        DrawingMode.entries.forEach { mode ->
            screen(
                Routes.resultPattern(mode),
                arguments = listOf(
                    navArgument(Routes.ARG_STARTED_AT) { type = NavType.LongType },
                    navArgument(Routes.ARG_ENDED_AT) { type = NavType.LongType }
                )
            ) { entry ->
                val arguments = requireNotNull(entry.arguments)
                fun leaveTo(route: String) = navController.navigate(route) {
                    popUpTo(Routes.MODE_SELECT)
                }
                ChallengeResultRoute(
                    mode = mode,
                    startedAtMillis = arguments.getLong(Routes.ARG_STARTED_AT),
                    endedAtMillis = arguments.getLong(Routes.ARG_ENDED_AT),
                    onTryAgain = { leaveTo(Routes.ready(mode)) },
                    onModeSelect = {
                        navController.popBackStack(Routes.MODE_SELECT, inclusive = false)
                    },
                    onMyDrawings = { leaveTo(Routes.MY_DRAWINGS) },
                    onTerritoryMap = { leaveTo(Routes.territoryMap(it)) },
                    onCircleRanking = { leaveTo(Routes.CIRCLE_RANKING) }
                )
            }
        }
        screen(Routes.MY_DRAWINGS) {
            MyDrawingsRoute(
                onStartChallenge = {
                    navController.navigate(Routes.CIRCLE_READY) {
                        popUpTo(Routes.MY_DRAWINGS) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        screen(
            Routes.TERRITORY_MAP,
            arguments = listOf(Routes.ARG_LATITUDE, Routes.ARG_LONGITUDE).map {
                navArgument(it) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            }
        ) { entry ->
            val latitude = entry.arguments?.getString(Routes.ARG_LATITUDE)?.toDoubleOrNull()
            val longitude = entry.arguments?.getString(Routes.ARG_LONGITUDE)?.toDoubleOrNull()
            TerritoryMapRoute(
                initialCenter = if (latitude != null && longitude != null) {
                    RoutePoint(latitude, longitude)
                } else {
                    null
                },
                onBack = { navController.popBackStack() }
            )
        }
        screen(Routes.CIRCLE_RANKING) {
            CircleRankingRoute(onBack = { navController.popBackStack() })
        }
        screen(Routes.GALLERY) {
            PublicGalleryRoute(
                onOpenArtwork = { navController.navigate(Routes.galleryArtwork(it)) },
                onBack = { navController.popBackStack() }
            )
        }
        screen(
            Routes.GALLERY_ARTWORK,
            arguments = listOf(navArgument(Routes.ARG_ARTWORK_ID) { type = NavType.StringType })
        ) { entry ->
            PublicArtworkDetailRoute(
                artworkId = requireNotNull(entry.arguments?.getString(Routes.ARG_ARTWORK_ID)),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private typealias ScreenExitTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?

private fun NavGraphBuilder.gatedComposable(
    route: String,
    arguments: List<NamedNavArgument>,
    restriction: State<DrivingRestriction>,
    exitTransition: ScreenExitTransition?,
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route, arguments, exitTransition = exitTransition) { entry ->
    val current by restriction
    StopNoticeGate(reason = stopNoticeReasonOf(route, current)) { content(entry) }
}
