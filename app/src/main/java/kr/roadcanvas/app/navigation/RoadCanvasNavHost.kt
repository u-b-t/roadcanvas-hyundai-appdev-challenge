package kr.roadcanvas.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kr.roadcanvas.app.modeselect.ModeSelectScreen
import kr.roadcanvas.app.splash.SPLASH_DURATION_MILLIS
import kr.roadcanvas.app.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val MODE_SELECT = "modeSelect"

    // 준비 화면은 #17에서 실제 화면으로 교체한다.
    const val CIRCLE_READY = "circleReady"

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
            PlaceholderScreen(title = CIRCLE_READY_TITLE)
        }
        composable(Routes.MY_DRAWINGS) {
            PlaceholderScreen(title = MY_DRAWINGS_TITLE)
        }
    }
}
