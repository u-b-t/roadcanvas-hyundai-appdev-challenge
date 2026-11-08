package kr.roadcanvas.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.shopl.sdg_common.foundation.typography.SDGTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadCanvasThemeTest {
    private val typography = RoadCanvasTypography
    private val allStyles: Map<String, TextStyle> = mapOf(
        "displayLarge" to typography.displayLarge,
        "displayMedium" to typography.displayMedium,
        "displaySmall" to typography.displaySmall,
        "headlineLarge" to typography.headlineLarge,
        "headlineMedium" to typography.headlineMedium,
        "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge,
        "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall,
        "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
        "labelMedium" to typography.labelMedium,
        "labelSmall" to typography.labelSmall
    )

    @Test
    fun `차량용 글자는 SDG 모바일 크기보다 1_25배 크다`() {
        val scaled = SDGTypography.Body1SB.forCar()

        assertEquals(20f, scaled.fontSize.value, 0.01f)
        assertEquals(25f, scaled.lineHeight.value, 0.01f)
    }

    @Test
    fun `차량용 글자는 SDG Pretendard 글꼴을 유지한다`() {
        assertEquals(
            SDGTypography.Body1SB.style.fontFamily,
            SDGTypography.Body1SB.forCar().fontFamily
        )
        allStyles.forEach { (name, style) ->
            assertTrue("$name 글꼴이 SDG 글꼴이어야 한다", style.fontFamily in sdgFontFamilies)
        }
    }

    @Test
    fun `모든 글자 역할은 차량에서 읽을 수 있는 크기 이상이다`() {
        allStyles.forEach { (name, style) ->
            assertTrue("$name 크기 ${style.fontSize}", style.fontSize >= 12.sp)
            assertTrue("$name 줄 높이가 글자보다 작다", style.lineHeight >= style.fontSize)
        }
    }

    @Test
    fun `큰 역할일수록 글자가 작아지지 않는다`() {
        val ordered = listOf(
            typography.displayLarge,
            typography.displaySmall,
            typography.headlineLarge,
            typography.headlineMedium,
            typography.headlineSmall,
            typography.titleLarge,
            typography.titleMedium,
            typography.titleSmall
        )
        ordered.zipWithNext().forEach { (larger, smaller) ->
            assertTrue(
                "${larger.fontSize} < ${smaller.fontSize}",
                larger.fontSize >= smaller.fontSize
            )
        }
    }

    @Test
    fun `라이트 색 구성만 쓴다`() {
        val scheme = RoadCanvasColorScheme

        assertTrue(scheme.background.luminance() > 0.9f)
        assertTrue(scheme.surface.luminance() > 0.9f)
        assertTrue(scheme.onBackground.luminance() < 0.1f)
    }

    @Test
    fun `글자와 배경 색은 충분히 대비된다`() {
        val scheme = RoadCanvasColorScheme
        // 본문 글자는 4.5:1, 버튼처럼 큰 글자만 올리는 색은 3:1 이상이어야 한다.
        assertContrastAtLeast(4.5f, scheme.onBackground, scheme.background)
        assertContrastAtLeast(4.5f, scheme.onSurface, scheme.surface)
        assertContrastAtLeast(4.5f, scheme.onSurfaceVariant, scheme.surfaceVariant)
        assertContrastAtLeast(4.5f, scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrastAtLeast(4.5f, scheme.onTertiaryContainer, scheme.tertiaryContainer)
        assertContrastAtLeast(4.5f, scheme.inverseOnSurface, scheme.inverseSurface)
        assertContrastAtLeast(3f, scheme.onPrimary, scheme.primary)
        assertContrastAtLeast(3f, scheme.onSecondary, scheme.secondary)
        assertContrastAtLeast(3f, scheme.onTertiary, scheme.tertiary)
        assertContrastAtLeast(3f, scheme.onError, scheme.error)
        assertContrastAtLeast(3f, scheme.error, scheme.background)
    }

    private val sdgFontFamilies = setOf(
        SDGTypography.Body1R.style.fontFamily,
        SDGTypography.Body1SB.style.fontFamily
    )

    private fun assertContrastAtLeast(minimum: Float, foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("대비 $ratio < $minimum ($foreground / $background)", ratio >= minimum)
    }
}
