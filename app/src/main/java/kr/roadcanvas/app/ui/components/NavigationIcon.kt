package kr.roadcanvas.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class NavigationIcon { Home, Drawings, Profile, Start }

private fun navigationImage(icon: NavigationIcon): ImageVector = ImageVector.Builder(
    name = icon.name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        when (icon) {
            NavigationIcon.Home -> {
                moveTo(12f, 3f)
                lineTo(2f, 12f)
                horizontalLineTo(5f)
                verticalLineTo(21f)
                horizontalLineTo(10f)
                verticalLineTo(15f)
                horizontalLineTo(14f)
                verticalLineTo(21f)
                horizontalLineTo(19f)
                verticalLineTo(12f)
                horizontalLineTo(22f)
                close()
            }
            NavigationIcon.Drawings -> {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
                moveTo(6f, 6f)
                verticalLineTo(17f)
                lineTo(10f, 12f)
                lineTo(13f, 15f)
                lineTo(16f, 11f)
                lineTo(18f, 14f)
                verticalLineTo(6f)
                close()
            }
            NavigationIcon.Profile -> {
                moveTo(12f, 3f)
                curveTo(9f, 3f, 7f, 5f, 7f, 8f)
                curveTo(7f, 11f, 9f, 13f, 12f, 13f)
                curveTo(15f, 13f, 17f, 11f, 17f, 8f)
                curveTo(17f, 5f, 15f, 3f, 12f, 3f)
                close()
                moveTo(4f, 21f)
                curveTo(4f, 17f, 7f, 15f, 12f, 15f)
                curveTo(17f, 15f, 20f, 17f, 20f, 21f)
                close()
            }
            NavigationIcon.Start -> {
                moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()
            }
        }
    }
}.build()

@Composable
fun NavigationButtonIcon(icon: NavigationIcon) {
    Icon(
        imageVector = navigationImage(icon),
        contentDescription = null,
        modifier = Modifier.testTag("buttonIcon${icon.name}")
    )
}
