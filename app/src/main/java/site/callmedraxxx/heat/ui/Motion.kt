package site.callmedraxxx.heat.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * One easing and one clock for the whole app. Everything here collapses to an instant
 * cut when the phone's "Remove animations" setting is on.
 */
object Motion {
    val ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 1 normally, 0 when the person turned animations off in Android's accessibility settings. */
    @Composable
    fun scale(): Float {
        val ctx = LocalContext.current
        return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceIn(0f, 1f) }
    }

    @Composable
    fun ms(base: Int) = (base * scale()).toInt()
}

/**
 * The screen as the person set it up: dp width follows the display size and the
 * developer "smallest width" setting, so every breakpoint reads it live.
 */
object Screen {
    val width: androidx.compose.ui.unit.Dp @Composable get() = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val height: androidx.compose.ui.unit.Dp @Composable get() = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    /** Narrow phones, or a phone with a large "smallest width": big type steps down a size. */
    val compact: Boolean @Composable get() = width < 370.dp
    /** Tablets and unfolded foldables: content stays at a phone's width, centred. */
    val wide: Boolean @Composable get() = width >= 600.dp
    val short: Boolean @Composable get() = height < 520.dp
}

/** True once, a frame after the screen first shows: the cue for its entrance. */
@Composable
fun rememberShown(): Boolean {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    return shown
}

/** Fade and rise into place, the [index]th child a beat after the one before. */
fun Modifier.rise(shown: Boolean, index: Int): Modifier = composed {
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(Motion.ms(520), Motion.ms(50) * index, Motion.ease), label = "rise")
    graphicsLayer { alpha = a; translationY = (1 - a) * 28f }
}

/** A physical push: the control shrinks a touch while the finger is down. */
fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(Motion.ms(140), easing = Motion.ease), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** A number that counts up to [target] when it first appears, and glides when it changes. */
@Composable
fun countTo(target: Int): Int {
    val scale = Motion.scale()
    val v = remember { Animatable(if (scale == 0f) target.toFloat() else 0f) }
    LaunchedEffect(target) { v.animateTo(target.toFloat(), tween((900 * scale).toInt(), easing = Motion.ease)) }
    return v.value.toInt()
}
