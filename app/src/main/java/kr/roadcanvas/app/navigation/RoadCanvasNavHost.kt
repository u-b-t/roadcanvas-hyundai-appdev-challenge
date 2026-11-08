package kr.roadcanvas.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kr.roadcanvas.app.modeselect.ModeSelectScreen
import kr.roadcanvas.app.mydrawings.MyDrawingsRoute
import kr.roadcanvas.app.splash.SPLASH_DURATION_MILLIS
import kr.roadcanvas.app.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val MODE_SELECT = "modeSelect"

    const val CIRCLE_READY = "circleReady"
    const val CIRCLE_PROGRESS = "circleProgress"

    /**
     * 종료한 기록은 진행 중 기록 저장소에 그대로 남아 있고, 시작·종료 시각을 인자로 넘긴다.
     * 진행 중 기록은 한 번에 하나뿐이므로 시작 시각으로 그 기록을 확인한다.
     * 결과 화면은 이 기록으로 완료 기록을 저장하는 데 성공한 뒤 진행 중 기록을 지운다.
     */
    const val ARG_STARTED_AT = "startedAtMillis"
    const val ARG_ENDED_AT = "endedAtMillis"
    const val CIRCLE_RESULT = "circleResult/{$ARG_STARTED_AT}/{$ARG_ENDED_AT}"

    fun circleResult(startedAtMillis: Long, endedAtMillis: Long) =
        "circleResult/$startedAtMillis/$endedAtMillis"

    const val MY_DRAWINGS = "myDrawings"
}

@Composable
fun RoadCanvasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    splashDurationMillis: Long = SPLASH_DURATION_MILLIS
) {
    val recorder = LocalContext.current.appContainer.challengeRecorder
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            val scope = rememberCoroutineScope()
            SplashScreen(
                durationMillis = splashDurationMillis,
                onFinished = {
                    scope.launch {
                        // 종료했지만 저장하지 못한 도전은 결과 화면이 다시 저장하고,
                        // 진행 중이던 도전은 진행 화면이 기록을 이어간다.
                        val unsaved = recorder.unsavedFinish()
                        val resume = unsaved == null && recorder.hasInProgress()
                        navController.navigate(Routes.MODE_SELECT) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        when {
                            unsaved != null -> navController.navigate(
                                Routes.circleResult(unsaved.startedAtMillis, unsaved.endedAtMillis)
                            )
                            resume -> navController.navigate(Routes.CIRCLE_PROGRESS)
                        }
                    }
                }
            )
        }
        composable(Routes.MODE_SELECT) {
            ModeSelectScreen(
                onCircleChallengeClick = { navController.navigate(Routes.CIRCLE_READY) },
                onMyDrawingsClick = { navController.navigate(Routes.MY_DRAWINGS) }
            )
        }
        composable(Routes.CIRCLE_READY) {
            val startChallenge = rememberChallengeStarter(
                onStarted = {
                    navController.navigate(Routes.CIRCLE_PROGRESS) {
                        popUpTo(Routes.CIRCLE_READY) { inclusive = true }
                    }
                }
            )
            ChallengeReadyRoute(
                onStart = startChallenge,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CIRCLE_PROGRESS) {
            fun leaveTo(route: String) = navController.navigate(route) {
                popUpTo(Routes.CIRCLE_PROGRESS) { inclusive = true }
            }
            ChallengeProgressRoute(
                onFinished = { leaveTo(Routes.circleResult(it.startedAtMillis, it.endedAtMillis)) },
                onGaveUp = { leaveTo(Routes.CIRCLE_READY) },
                onBackToReady = { leaveTo(Routes.CIRCLE_READY) }
            )
        }
        composable(
            Routes.CIRCLE_RESULT,
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
                startedAtMillis = arguments.getLong(Routes.ARG_STARTED_AT),
                endedAtMillis = arguments.getLong(Routes.ARG_ENDED_AT),
                onTryAgain = { leaveTo(Routes.CIRCLE_READY) },
                onModeSelect = {
                    navController.popBackStack(Routes.MODE_SELECT, inclusive = false)
                },
                onMyDrawings = { leaveTo(Routes.MY_DRAWINGS) }
            )
        }
        composable(Routes.MY_DRAWINGS) {
            MyDrawingsRoute(
                onStartChallenge = {
                    navController.navigate(Routes.CIRCLE_READY) {
                        popUpTo(Routes.MY_DRAWINGS) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
