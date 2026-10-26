package site.callmedraxxx.heat.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.LogEntry
import site.callmedraxxx.heat.data.Store
import site.callmedraxxx.heat.heat.Level
import site.callmedraxxx.heat.remind.Notify
import kotlin.math.roundToInt

/** The full-screen warning an hour before Danger, shown over the lock screen. */
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val place = intent.getStringExtra("place") ?: ""
        val from = intent.getIntExtra("from", 12)
        val to = intent.getIntExtra("to", 16)
        val peak = intent.getDoubleExtra("peak", 41.0)
        val level = runCatching { Level.valueOf(intent.getStringExtra("level")!!) }.getOrDefault(Level.DANGER)
        val f = Store(this).forecast()
        val bars = f?.on(f.today())?.filter { it.time.hour in (from - 1)..(to + 2) }.orEmpty()
        val hr = Store(this).profile.higherRisk

        C.look = Looks.byId(Store(this).look)
        setContent {
            HeatTheme {
                Box(Modifier.fillMaxSize().background(levelColor(level))) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 30.dp)) {
                        Text(getString(R.string.alert_kicker), style = T.label.copy(color = onLevel(level)))
                        Spacer(Modifier.height(14.dp))
                        val name = levelName(this@AlertActivity, level).lowercase()
                        val full = getString(R.string.alert_title, place, name, from)
                        Text(buildAnnotatedString {
                            val i = full.indexOf(name)
                            if (i < 0) append(full) else {
                                append(full.substring(0, i))
                                withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(name) }
                                append(full.substring(i + name.length))
                            }
                        }, style = T.title.copy(fontSize = 44.sp, lineHeight = 48.sp, color = onLevel(level)))
                        Spacer(Modifier.height(16.dp))
                        Text(getString(R.string.alert_body, peak.roundToInt(), to), style = T.body.copy(fontSize = 18.sp, lineHeight = 26.sp, color = onLevel(level)))
                        Spacer(Modifier.height(26.dp))
                        if (bars.isNotEmpty()) Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom) {
                            val top = bars.maxOf { it.heatIndex }
                            bars.forEach { h ->
                                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(((h.heatIndex - 20) / (top - 20)).toFloat().coerceIn(.12f, 1f) * .85f)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                        .background(onLevel(level).copy(alpha = if (h.level(hr) >= Level.DANGER) 1f else .45f)))
                                    Text("%02d".format(h.time.hour), style = T.small.copy(fontSize = 11.sp, color = onLevel(level)), modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        BigButton(getString(R.string.alert_safe), fill = C.strong, textColor = if (C.look.dark) C.text else C.bg, height = 96.dp) {
                            Store(this@AlertActivity).addLog(LogEntry.OK)
                            Notify.cancel(this@AlertActivity, Notify.ID_ALERT); finish()
                        }
                        Spacer(Modifier.height(10.dp))
                        QuietButton(getString(R.string.alert_what)) {
                            Notify.cancel(this@AlertActivity, Notify.ID_ALERT)
                            startActivity(Intent(this@AlertActivity, MainActivity::class.java)); finish()
                        }
                    }
                }
            }
        }
    }
}
