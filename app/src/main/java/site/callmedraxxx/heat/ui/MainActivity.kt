package site.callmedraxxx.heat.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.togetherWith
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Forecast
import site.callmedraxxx.heat.data.Locator
import site.callmedraxxx.heat.data.Place
import site.callmedraxxx.heat.data.Store
import site.callmedraxxx.heat.remind.Engine
import site.callmedraxxx.heat.remind.Notify

/** What the screens read. Rebuilt from the Store whenever something changes. */
class AppState(private val ctx: Context, private val scope: CoroutineScope) {
    val store = Store(ctx)
    var version by mutableIntStateOf(0)
        private set
    var forecast by mutableStateOf<Forecast?>(store.forecast())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Something in the Store changed: redraw, and let the alarms catch up. */
    fun touched() {
        forecast = store.forecast()
        version++
        scope.launch(Dispatchers.Default) { Engine.plan(ctx) }
    }

    fun refresh() {
        if (store.place == null || loading) return
        loading = true; failed = false
        scope.launch {
            withContext(Dispatchers.IO) { Engine.refresh(ctx) }
            val f = store.forecast()
            failed = f == null || System.currentTimeMillis() - f.fetchedAt > 3 * 3_600_000L
            forecast = f; version++; loading = false
        }
    }

    /** Follow the phone: find it now, and again each time the app opens. */
    fun followMe() {
        store.followMe = true
        locate()
    }

    /** Where the phone is now. A new place only when it has moved to another ~1 km square. */
    fun locate() {
        if (!store.followMe || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            val here = withContext(Dispatchers.IO) { Locator.here(ctx) } ?: return@launch
            val named = if (here.name.isBlank()) here.copy(name = ctx.getString(R.string.your_location)) else here
            if (named.key != store.place?.key) setPlace(named, follow = true) else touched()
        }
    }

    fun setPlace(p: Place, follow: Boolean = false) {
        store.followMe = follow
        store.place = p
        store.saveForecast("", 0)
        forecast = null
        version++
        refresh()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.channels(this)
        val startTab = intent.getStringExtra("tab")
        C.look = Looks.byId(Store(this).look)
        setContent {
            // Status bar icons dark on the light looks, light on the dark one.
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !C.look.dark; isAppearanceLightNavigationBars = !C.look.dark
                }
            }
            HeatTheme { App(startTab) }
        }
    }
}

enum class Tab(val label: Int) { TODAY(R.string.tab_today), WATER(R.string.tab_water), SICK(R.string.tab_sick), FAMILY(R.string.tab_family) }

@Composable
fun App(startTab: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { AppState(ctx, scope) }
    // Reading the version here redraws the whole app when the Store changes (a place picked, settings saved).
    @Suppress("UNUSED_VARIABLE") val v = app.version
    var tab by remember { mutableStateOf(when (startTab) { "water" -> Tab.WATER; "family", "checkin" -> Tab.FAMILY; else -> Tab.TODAY }) }
    var settings by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        app.store.markOpened()
        app.locate()
        val f = app.forecast
        if (f == null || System.currentTimeMillis() - f.fetchedAt > 30 * 60_000L) app.refresh() else app.touched()
    }

    // A town picked by hand before following existed: offer location once, then follow it.
    val locate = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) app.followMe() }
    LaunchedEffect(Unit) {
        val s = app.store
        if (s.place != null && !s.followMe && !s.askedLocation) { s.askedLocation = true; locate.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.touched() }
    LaunchedEffect(app.store.place != null) {
        if (app.store.place != null && Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val glow = C.look.glow
    Box(Modifier.fillMaxSize().background(C.bg).then(if (glow == null) Modifier else Modifier.background(
        Brush.radialGradient(listOf(glow, C.bg), radius = 1400f, center = androidx.compose.ui.geometry.Offset(540f, 0f)))),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
      Box(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
        when {
            app.store.place == null -> Welcome(app)
            settings -> Settings(app) { settings = false }
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                val ms = Motion.ms(260)
                androidx.compose.animation.AnimatedContent(tab, Modifier.weight(1f), transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(ms, ms / 3)) +
                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(ms, ms / 3), initialScale = .98f))
                        .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(ms / 3)))
                }, label = "tab") { t ->
                    when (t) {
                        Tab.TODAY -> Today(app, onSettings = { settings = true }, onWater = { tab = Tab.WATER }, onFamily = { tab = Tab.FAMILY })
                        Tab.WATER -> Water(app)
                        Tab.SICK -> Sick(app)
                        Tab.FAMILY -> Family(app)
                    }
                }
                NavBar(tab) { tab = it }
            }
        }
      }
    }
}

@Composable
private fun NavBar(current: Tab, onPick: (Tab) -> Unit) = Row(
    Modifier.background(C.bg).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceAround,
) {
    // Four equal columns, one line each, so no tab name ever breaks on a narrow screen.
    val size = if (Screen.compact) 13.sp else 14.sp
    Tab.entries.forEach { t ->
        val on = t == current
        Text(stringResource(t.label), style = (if (on) T.label.copy(color = C.text) else T.small).copy(fontSize = size),
            maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onPick(t) }.padding(horizontal = 4.dp, vertical = 16.dp))
    }
}
