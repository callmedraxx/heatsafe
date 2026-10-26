package site.callmedraxxx.heat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import site.callmedraxxx.heat.BuildConfig
import site.callmedraxxx.heat.R
import site.callmedraxxx.heat.remind.Speak
import kotlin.concurrent.thread
import site.callmedraxxx.heat.data.FamilyPlace
import site.callmedraxxx.heat.heat.HeatMath
import kotlin.math.roundToInt

@Composable
fun Family(app: AppState) {
    @Suppress("UNUSED_VARIABLE") val v = app.version
    val ctx = LocalContext.current
    val s = app.store
    var smsOk by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) }
    val askSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsOk = it }
    var adding by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.family_title), style = T.title, modifier = Modifier.padding(top = 14.dp))

        Panel {
            Text(stringResource(R.string.checkin_title), style = T.head)
            Text(stringResource(R.string.checkin_body), style = T.small)
            Toggle(stringResource(R.string.checkin_on), s.checkinOn) { s.checkinOn = it; app.touched() }
            if (s.checkinOn) {
                Field(stringResource(R.string.your_name), s.profile.name) { s.profile = s.profile.copy(name = it) }
                Field(stringResource(R.string.contact_name), s.contactName) { s.contactName = it }
                Field(stringResource(R.string.contact_phone), s.contactPhone, KeyboardType.Phone) { s.contactPhone = it }
                if (smsOk) Text(stringResource(R.string.sms_granted), style = T.small.copy(color = C.safe))
                else QuietButton(stringResource(R.string.sms_permission)) { askSms.launch(Manifest.permission.SEND_SMS) }
            }
        }

        Panel {
            Text(stringResource(R.string.places_title), style = T.head)
            Text(stringResource(R.string.places_body), style = T.small)
            s.familyPlaces.forEach { fp ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${fp.label} · ${fp.place.name}", style = T.body.copy(color = C.text))
                        val max = fp.maxHeatIndex
                        if (max == null) Text(stringResource(R.string.place_unknown), style = T.small)
                        else HeatMath.level(max).let { l ->
                            Text(stringResource(R.string.place_today, levelName(ctx, l), max.roundToInt()), style = T.small.copy(color = levelColor(l)))
                        }
                    }
                    Text(stringResource(R.string.remove), style = T.small, modifier = Modifier.clickable {
                        s.familyPlaces = s.familyPlaces - fp; app.touched()
                    }.padding(8.dp))
                }
            }
            if (s.familyPlaces.size < 3) {
                if (!adding) QuietButton(stringResource(R.string.add_place)) { adding = true }
                else {
                    Field(stringResource(R.string.label_hint), label) { label = it }
                    PlaceSearch { p ->
                        s.familyPlaces = s.familyPlaces + FamilyPlace(label.ifBlank { p.name }, p)
                        adding = false; label = ""; app.refresh()
                    }
                }
            }
        }
        QuietButton(stringResource(R.string.share)) { share(ctx) }
        Column(Modifier.padding(bottom = 12.dp)) {}
    }
}

/** Send HeatSafe to someone: the video's "send it to someone who works outside". */
fun share(ctx: android.content.Context) = ctx.startActivity(Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_text, DOWNLOAD_URL)), null))

@Composable
fun Settings(app: AppState, onClose: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = app.version
    val ctx = LocalContext.current
    val s = app.store
    var changing by remember { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) app.followMe() }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings), style = T.title, modifier = Modifier.weight(1f))
            IconTap(Icons.Rounded.Close, stringResource(R.string.cancel), onClick = onClose)
        }

        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            Panel {
                Text(stringResource(R.string.notifications_off), style = T.body)
                QuietButton(stringResource(R.string.turn_on)) {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
                }
            }

        Panel {
            Text(s.place?.let { "${it.name}${if (it.country.isNotBlank()) ", " + it.country else ""}" } ?: "", style = T.head)
            Toggle(stringResource(R.string.follow_me), s.followMe) { on ->
                if (!on) { s.followMe = false; app.touched() }
                else if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) app.followMe()
                else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!changing) QuietButton(stringResource(R.string.change_place)) { changing = true }
            else PlaceSearch { app.setPlace(it); changing = false; onClose() }
        }

        Panel {
            Text(stringResource(R.string.look), style = T.head)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Looks.all.forEach { l ->
                    val on = C.look == l
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(l.bg)
                        .then(if (on) Modifier.border(2.5.dp, C.accent, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { C.look = l; s.look = l.id; app.touched() }.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            l.levels.forEach { c -> androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c)) }
                        }
                        Text(stringResource(l.name), style = T.body.copy(color = l.text, fontFamily = l.display, fontWeight = androidx.compose.ui.text.font.FontWeight(l.displayWeight.coerceAtLeast(500))),
                            modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }

        Panel {
            Text(stringResource(R.string.about_you), style = T.head)
            Text(stringResource(R.string.about_you_body), style = T.small)
            val p = s.profile
            Toggle(stringResource(R.string.p_older), p.olderOrChild) { s.profile = p.copy(olderOrChild = it); app.touched() }
            Toggle(stringResource(R.string.p_pregnant), p.pregnant) { s.profile = p.copy(pregnant = it); app.touched() }
            Toggle(stringResource(R.string.p_outdoor), p.outdoorWorker) { s.profile = p.copy(outdoorWorker = it); app.touched() }
            Toggle(stringResource(R.string.p_medicine), p.medicine) { s.profile = p.copy(medicine = it); app.touched() }
            Toggle(stringResource(R.string.p_fluid), p.fluidLimit) { s.profile = p.copy(fluidLimit = it); app.touched() }
        }

        Panel {
            Toggle(stringResource(R.string.reminders), s.remindersOn) { s.remindersOn = it; app.touched() }
            Toggle(stringResource(R.string.speak_reminders), s.speakOn) {
                s.speakOn = it; app.touched()
                if (it) thread { Speak.say(ctx, ctx.getString(R.string.say_water)) }
            }
            Stepper(stringResource(R.string.wake_time), s.wakeMinute) { s.wakeMinute = it; app.touched() }
            Stepper(stringResource(R.string.sleep_time), s.sleepMinute) { s.sleepMinute = it; app.touched() }
        }

        Panel {
            Text(stringResource(R.string.about), style = T.small)
            Text(stringResource(R.string.source_code), style = T.body.copy(color = C.accent), modifier = Modifier.clickable {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
            })
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = T.small)
        }
        QuietButton(stringResource(R.string.share)) { share(ctx) }
        Column(Modifier.padding(bottom = 12.dp)) {}
    }
}

const val SOURCE_URL = "https://github.com/callmedraxx/heatsafe"
const val DOWNLOAD_URL = "$SOURCE_URL/releases/latest"

@Composable
fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, style = T.body.copy(color = C.text), modifier = Modifier.weight(1f).padding(end = 12.dp))
    Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = C.text))
}

/** A time of day, moved in half-hour steps. */
@Composable
private fun Stepper(text: String, minute: Int, onChange: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, style = T.body.copy(color = C.text), modifier = Modifier.weight(1f))
    IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.earlier), C.accent) { onChange(Math.floorMod(minute - 30, 1440)) }
    Text("%02d:%02d".format(minute / 60, minute % 60), style = T.head)
    IconTap(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.later), C.accent) { onChange(Math.floorMod(minute + 30, 1440)) }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(text, { text = it; onChange(it) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label, style = T.small) }, textStyle = T.body.copy(color = C.text), shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = C.line, cursorColor = C.accent))
}
