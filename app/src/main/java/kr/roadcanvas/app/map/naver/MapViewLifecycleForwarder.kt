package kr.roadcanvas.app.map.naver

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.naver.maps.map.MapView

/**
 * 화면 생명주기를 [MapView]에 전달한다.
 * 화면을 나가 Composable이 사라지면 [destroy]로 현재 단계에서 차례로 내려가 리소스를 해제한다.
 * 이미 해제했으면 다시 호출하지 않는다.
 */
internal class MapViewLifecycleForwarder(
    private val mapView: MapView,
    private val beforeDestroy: () -> Unit
) : LifecycleEventObserver {
    private var current = Lifecycle.State.INITIALIZED

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        moveTo(event.targetState)
    }

    fun destroy() = moveTo(Lifecycle.State.DESTROYED)

    private fun moveTo(target: Lifecycle.State) {
        while (current != Lifecycle.State.DESTROYED && current != target) {
            current = if (current < target) stepUp() else stepDown()
        }
    }

    private fun stepUp(): Lifecycle.State = when (current) {
        Lifecycle.State.INITIALIZED -> {
            mapView.onCreate(null)
            Lifecycle.State.CREATED
        }
        Lifecycle.State.CREATED -> {
            mapView.onStart()
            Lifecycle.State.STARTED
        }
        else -> {
            mapView.onResume()
            Lifecycle.State.RESUMED
        }
    }

    private fun stepDown(): Lifecycle.State = when (current) {
        Lifecycle.State.RESUMED -> {
            mapView.onPause()
            Lifecycle.State.STARTED
        }
        Lifecycle.State.STARTED -> {
            mapView.onStop()
            Lifecycle.State.CREATED
        }
        Lifecycle.State.CREATED -> {
            beforeDestroy()
            mapView.onDestroy()
            Lifecycle.State.DESTROYED
        }
        else -> Lifecycle.State.DESTROYED
    }
}
