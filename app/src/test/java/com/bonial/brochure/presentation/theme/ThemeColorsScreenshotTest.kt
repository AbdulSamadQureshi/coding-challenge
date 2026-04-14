package com.bonial.brochure.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the visual contract of the status palette used on both the character list
 * (dot on a dark badge) and the detail screen (tinted chip with label). A shift
 * in any of the nine status colors shows up as a diff on the next CI run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// sdk pinned because Robolectric's bundled frameworks trail the project's targetSdk.
@Config(sdk = [34], qualifiers = "w360dp-h640dp-normal-long-notround-any-420dpi-keyshidden-nonav")
class ThemeColorsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusPalette() {
        composeRule.setContent {
            Column(modifier = Modifier.background(Color.White).padding(16.dp)) {
                ListBadgeRow("Alive", StatusAlive)
                Spacer(Modifier.height(8.dp))
                ListBadgeRow("Dead", StatusDead)
                Spacer(Modifier.height(8.dp))
                ListBadgeRow("Unknown", StatusUnknown)
                Spacer(Modifier.height(16.dp))
                DetailChipRow("Alive", StatusAliveBg, StatusAlive, StatusAliveText)
                Spacer(Modifier.height(8.dp))
                DetailChipRow("Dead", StatusDeadBg, StatusDead, StatusDeadText)
                Spacer(Modifier.height(8.dp))
                DetailChipRow("Unknown", StatusUnknownBg, StatusUnknown, StatusUnknownText)
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}

@Composable
private fun ListBadgeRow(label: String, dot: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(7.dp).clip(CircleShape).background(dot),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, color = Color.White)
    }
}

@Composable
private fun DetailChipRow(label: String, bg: Color, dot: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .background(color = bg, shape = CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp).clip(CircleShape).background(dot),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = textColor)
    }
}
