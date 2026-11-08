package kr.roadcanvas.app.test

import android.app.Application
import android.content.Context
import io.cucumber.android.runner.CucumberAndroidJUnitRunner

/** Cucumber 실행은 그대로 두고 Application만 [TestRoadCanvasApplication]으로 바꾼다. */
class RoadCanvasTestRunner : CucumberAndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?
    ): Application = super.newApplication(
        classLoader,
        TestRoadCanvasApplication::class.java.name,
        context
    )
}
