package kr.roadcanvas.app.ui.components

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import kr.roadcanvas.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverBackgroundColorTest {
    @Test
    fun `앱 시작 창 배경색은 화면의 커버 배경색과 같다`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(COVER_BACKGROUND_COLOR.toArgb(), context.getColor(R.color.cover_background))
    }
}
