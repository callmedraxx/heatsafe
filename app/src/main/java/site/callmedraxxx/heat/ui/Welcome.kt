package site.callmedraxxx.heat.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.data.Locator
import site.callmedraxxx.heat.data.Place
import site.callmedraxxx.heat.data.Weather

@Composable
fun Welcome(app: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val locate = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) { status = ctx.getString(R.string.no_location); return@rememberLauncherForActivityResult }
        status = ctx.getString(R.string.locating)
        scope.launch {
            val p = withContext(Dispatchers.IO) { Locator.here(ctx) }
            if (p == null) status = ctx.getString(R.string.no_location)
            else app.setPlace(if (p.name.isBlank()) p.copy(name = ctx.getString(R.string.your_location)) else p, follow = true)
        }
    }
    LaunchedEffect(Unit) { locate.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 26.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.welcome_title), style = T.title.copy(fontSize = 40.sp, lineHeight = 44.sp))
        Text(stringResource(R.string.welcome_body), style = T.body)
        Spacer(Modifier.height(8.dp))
        BigButton(stringResource(R.string.use_location)) { locate.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
        status?.let { Text(it, style = T.small) }
        Text(stringResource(R.string.or_search), style = T.label)
        PlaceSearch { app.setPlace(it) }
    }
}

/** Search Open-Meteo's place list; tap a result to pick it. */
@Composable
fun PlaceSearch(onPick: (Place) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    fun go() = scope.launch {
        if (q.isBlank()) return@launch
        results = withContext(Dispatchers.IO) { runCatching { Weather.search(q.trim()) }.getOrDefault(emptyList()) }
    }
    // Search as they type, once they pause.
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(450)
        go()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = T.body.copy(color = C.text),
            placeholder = { Text(stringResource(R.string.search_hint), style = T.body.copy(color = C.faint)) },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = C.line, cursorColor = C.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { go() }))
        results?.let { list ->
            if (list.isEmpty()) Text(stringResource(R.string.search_none), style = T.small)
            list.forEach { p ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.card).clickable { onPick(p) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(p.name, style = T.head)
                    Text(p.country, style = T.small)
                }
            }
        }
    }
}
