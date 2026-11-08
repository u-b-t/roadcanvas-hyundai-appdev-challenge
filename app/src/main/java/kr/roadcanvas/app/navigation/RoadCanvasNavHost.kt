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
            ModeSelectScreen()
        }
    }
}
