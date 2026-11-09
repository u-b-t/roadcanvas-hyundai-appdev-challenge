package kr.roadcanvas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.roadcanvas.app.debug.VehicleIntegrationBadge
import kr.roadcanvas.app.navigation.RoadCanvasNavHost
import kr.roadcanvas.app.ui.theme.RoadCanvasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vehicleIntegrationInfo = appContainer.vehicleIntegrationInfo
        val drivingRestrictionProvider = appContainer.drivingRestrictionProvider
        setContent {
            RoadCanvasTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    RoadCanvasNavHost()
                    if (BuildConfig.DEBUG) {
                        VehicleIntegrationBadge(
                            info = vehicleIntegrationInfo,
                            drivingRestriction = drivingRestrictionProvider.restriction,
                            drivingRestrictionBasis = drivingRestrictionProvider.basis,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
