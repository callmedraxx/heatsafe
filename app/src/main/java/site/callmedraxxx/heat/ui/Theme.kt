package site.callmedraxxx.heat.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.heat.Level
import site.callmedraxxx.heat.heat.Tip

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

private val Sora = FontFamily(variable(R.font.sora, 300), variable(R.font.sora, 400), variable(R.font.sora, 600), variable(R.font.sora, 700))
private val Fraunces = FontFamily(variable(R.font.fraunces, 500), variable(R.font.fraunces, 700))
private val Atkinson = FontFamily(Font(R.font.atkinson_regular, FontWeight(400)), Font(R.font.atkinson_bold, FontWeight(700)))

/**
 * One look of the app: every colour and both typefaces. The layout is the same in all of
 * them; only this changes. [levels] fill cards and bars, [levelInk] is the same level as
 * readable text on [bg].
 */
class Look(
    val id: String, val name: Int, val dark: Boolean,
    val bg: Color, val glow: Color?, val card: Color, val line: Color, val strong: Color,
    val text: Color, val soft: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val water: Color, val onWater: Color,
    val levels: List<Color>, val levelInk: List<Color>, val onLevel: List<Color>,
    val display: FontFamily, val displayWeight: Int, val body: FontFamily, val bodyBold: Int,
)

object Looks {
    /** Direction C: warm paper, ink, sun-readable type. The default. */
    val Daylight = Look("daylight", R.string.look_daylight, dark = false,
        bg = Color(0xFFFBF6EC), glow = null, card = Color(0xFFFFFFFF), line = Color(0xFFE6DCCB), strong = Color(0xFF1D1A16),
        text = Color(0xFF1D1A16), soft = Color(0xFF3B352D), muted = Color(0xFF6B6358),
        accent = Color(0xFFE5432D), onAccent = Color.White, water = Color(0xFF1F6FD1), onWater = Color.White,
        levels = listOf(Color(0xFF7CC47F), Color(0xFFF2B84B), Color(0xFFF08A3C), Color(0xFFE5432D), Color(0xFF8E3CCB)),
        levelInk = listOf(Color(0xFF2E7D3A), Color(0xFF8A5A00), Color(0xFFB4501A), Color(0xFFC7321D), Color(0xFF7A2DB5)),
        onLevel = listOf(Color(0xFF1D1A16), Color(0xFF1D1A16), Color(0xFF1D1A16), Color.White, Color.White),
        display = Fraunces, displayWeight = 700, body = Atkinson, bodyBold = 700)

    /** Direction B: dark, calm, one hot accent per level. */
    val Thermal = Look("thermal", R.string.look_thermal, dark = true,
        bg = Color(0xFF0C0C0E), glow = Color(0xFF2A1208), card = Color(0xFF17161A), line = Color(0xFF26242A), strong = Color(0xFF3A363E),
        text = Color(0xFFF3EFE8), soft = Color(0xFFCFC7BB), muted = Color(0xFFA49D93),
        accent = Color(0xFFFF6A2B), onAccent = Color(0xFF140A05), water = Color(0xFF46C2FF), onWater = Color(0xFF04121F),
        levels = listOf(Color(0xFF3DDC97), Color(0xFFFFD23F), Color(0xFFFF9A3C), Color(0xFFFF4B2B), Color(0xFFB13CFF)),
        levelInk = listOf(Color(0xFF3DDC97), Color(0xFFFFD23F), Color(0xFFFF9A3C), Color(0xFFFF6A4F), Color(0xFFC77BFF)),
        onLevel = listOf(Color(0xFF0C0C0E), Color(0xFF0C0C0E), Color(0xFF0C0C0E), Color.White, Color.White),
        display = Sora, displayWeight = 300, body = Sora, bodyBold = 600)


    val all = listOf(Daylight, Thermal)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Daylight
}

/** The colours of the current look. Reading one inside a composable redraws it when the look changes. */
object C {
    var look by mutableStateOf(Looks.Daylight)

    val bg get() = look.bg
    val card get() = look.card
    val line get() = look.line
    val strong get() = look.strong
    val text get() = look.text
    val soft get() = look.soft
    val muted get() = look.muted
    val faint get() = look.muted
    val accent get() = look.accent
    val onAccent get() = look.onAccent
    val water get() = look.water
    val onWater get() = look.onWater
    val waterLight get() = look.water

    val safe get() = look.levels[0]
    val caution get() = look.levels[1]
    val extremeCaution get() = look.levels[2]
    val danger get() = look.levels[3]
    val extremeDanger get() = look.levels[4]
}

fun levelColor(l: Level) = C.look.levels[l.ordinal]
fun levelInk(l: Level) = C.look.levelInk[l.ordinal]
fun onLevel(l: Level) = C.look.onLevel[l.ordinal]

fun levelName(ctx: Context, l: Level) = ctx.getString(when (l) {
    Level.SAFE -> R.string.lvl_safe
    Level.CAUTION -> R.string.lvl_caution
    Level.EXTREME_CAUTION -> R.string.lvl_extreme_caution
    Level.DANGER -> R.string.lvl_danger
    Level.EXTREME_DANGER -> R.string.lvl_extreme_danger
})

fun tipText(ctx: Context, t: Tip) = ctx.getString(when (t) {
    Tip.SHADE -> R.string.tip_shade
    Tip.WET_FACE -> R.string.tip_wet_face
    Tip.DAMP_CLOTH -> R.string.tip_damp_cloth
    Tip.COOL_WASH -> R.string.tip_cool_wash
    Tip.WET_SKIN_FIRST -> R.string.tip_wet_skin
})

/** Type roles. Display carries numbers and headlines; body carries everything people read. */
object T {
    private fun display(size: TextUnit, line: TextUnit = TextUnit.Unspecified) = TextStyle(fontFamily = C.look.display,
        fontWeight = FontWeight(C.look.displayWeight), fontSize = size, lineHeight = line, letterSpacing = (-0.02 * size.value).sp, color = C.text)
    private fun body(size: TextUnit, line: TextUnit, bold: Boolean = false, colour: Color = C.soft) = TextStyle(fontFamily = C.look.body,
        fontWeight = FontWeight(if (bold) C.look.bodyBold else 400), fontSize = size, lineHeight = line, color = colour)

    val huge get() = display(84.sp, 84.sp)
    val title get() = display(32.sp, 36.sp)
    val head get() = body(18.sp, 24.sp, bold = true, colour = C.text)
    val body get() = body(16.sp, 23.sp)
    val small get() = body(14.sp, 19.sp, colour = C.muted)
    val label get() = body(13.sp, 17.sp, bold = true, colour = C.muted)
    val button get() = body(17.sp, 22.sp, bold = true)
}

@Composable
fun HeatTheme(content: @Composable () -> Unit) {
    val l = C.look
    MaterialTheme(
        colorScheme = if (l.dark) darkColorScheme(background = l.bg, surface = l.bg, primary = l.accent, onPrimary = l.onAccent,
            onBackground = l.text, onSurface = l.text)
        else lightColorScheme(background = l.bg, surface = l.bg, primary = l.accent, onPrimary = l.onAccent,
            onBackground = l.text, onSurface = l.text, outline = l.line),
        content = content,
    )
}
