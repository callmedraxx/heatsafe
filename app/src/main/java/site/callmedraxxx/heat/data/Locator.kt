package site.callmedraxxx.heat.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Where the phone is, from Android's own location service: no Google Play services,
 * so it also works on phones without them. Rounded to about 1 km straight away.
 */
object Locator {

    @SuppressLint("MissingPermission")
    suspend fun here(ctx: Context): Place? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = withTimeoutOrNull(15_000) {
            providers.firstNotNullOfOrNull { current(lm, it) }
        }
        val loc = fresh ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            ?: return null
        val lat = Place.round(loc.latitude)
        val lon = Place.round(loc.longitude)
        val (name, country, cc) = describe(ctx, loc)
        return Place(name, country, cc.ifBlank { countryFromNetwork(ctx) }, lat, lon)
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(lm: LocationManager, provider: String): Location? =
        if (Build.VERSION.SDK_INT >= 30) suspendCancellableCoroutine { c ->
            lm.getCurrentLocation(provider, null, { it.run() }) { c.resume(it) }
        } else suspendCancellableCoroutine { c ->
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, { c.resume(it) }, android.os.Looper.getMainLooper())
        }

    /** Town and country from the phone's geocoder, when it has one. */
    private fun describe(ctx: Context, loc: Location): Triple<String, String, String> = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        @Suppress("DEPRECATION")
        val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?: return@runCatching null
        Triple(a.locality ?: a.subAdminArea ?: a.adminArea ?: "", a.countryName ?: "", a.countryCode ?: "")
    }.getOrNull() ?: Triple("", "", "")

    /** The country the phone's mobile network is in, for the emergency number. */
    fun countryFromNetwork(ctx: Context): String = runCatching {
        val t = ctx.getSystemService(TelephonyManager::class.java)
        (t?.networkCountryIso?.takeIf { it.isNotBlank() } ?: t?.simCountryIso ?: "").uppercase()
    }.getOrDefault("")
}
