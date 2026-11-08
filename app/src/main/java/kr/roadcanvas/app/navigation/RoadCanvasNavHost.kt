package kr.roadcanvas.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kr.roadcanvas.app.challenge.ChallengeReadyRoute
import kr.roadcanvas.app.modeselect.ModeSelectScreen
import kr.roadcanvas.app.splash.SPLASH_DURATION_MILLIS
import kr.roadcanvas.app.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val MODE_SELECT = "modeSelect"

    const val CIRCLE_READY = "circleReady"

    // 진행 화면은 #18에서 실제 화면으로 교체한다.
    const val CIRCLE_PROGRESS = "circleProgress"

    // 내 그림 목록은 #20에서 실제 화면으로 교체한다.
    const val MY_DRAWINGS = "myDrawings"
}

@Composable
fun RoadCanvasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    splashDurationMillis: Long = SPLASH_DURATION_MILLIS
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                durationMillis = splashDurationMillis,
                onFinished = {
                    navController.navigate(Routes.MODE_SELECT) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
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
            ChallengeReadyRoute(
                // 기록 서비스 기동은 #18에서 진행 화면과 함께 연결한다.
                onStart = {
                    navController.navigate(Routes.CIRCLE_PROGRESS) {
                        popUpTo(Routes.CIRCLE_READY) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CIRCLE_PROGRESS) {
            PlaceholderScreen(title = CIRCLE_PROGRESS_TITLE)
        }
        composable(Routes.MY_DRAWINGS) {
            PlaceholderScreen(title = MY_DRAWINGS_TITLE)
        }
    }
}
