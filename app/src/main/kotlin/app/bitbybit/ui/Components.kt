package app.bitbybit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbType

/** Top bar used by lesson and workbench screens: back, eyebrow + title. */
@Composable
fun LevelTopBar(eyebrow: String, title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Back") { onBack() }
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = Bb.TextSecondary, fontSize = 28.sp) }
        Column(Modifier.padding(start = 4.dp)) {
            Text(eyebrow.uppercase(), style = BbType.Eyebrow)
            Text(title, style = BbType.Title)
        }
    }
}

enum class ButtonTone { Accent, Success, Ghost, Disabled, Error }

/** Full-width 54 dp primary button. Disabled buttons explain what is missing. */
@Composable
fun PrimaryButton(text: String, tone: ButtonTone, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val (bg, fg, border) = when (tone) {
        ButtonTone.Accent -> Triple(Bb.Accent, Bb.OnAccent, Color.Transparent)
        ButtonTone.Success -> Triple(Bb.High, Bb.OnHigh, Color.Transparent)
        ButtonTone.Ghost -> Triple(Color.Transparent, Bb.Text, Bb.Border)
        ButtonTone.Disabled -> Triple(Color(0xFF1F2530), Color(0xFFB4BCC8), Bb.Border)
        ButtonTone.Error -> Triple(Bb.ErrorBg, Bb.ErrorText, Bb.ErrorBorder)
    }
    val enabled = tone == ButtonTone.Accent || tone == ButtonTone.Success || tone == ButtonTone.Ghost
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Bb.Surface)
            .border(1.dp, Bb.Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
