# HeatSafe

Heat risk where you are, in plain words, with water reminders and a check-in on the worst days. Built for the El Niño heat seasons in Nigeria and West Africa, works anywhere Open-Meteo has a forecast.

Free, open source, no account, no server of its own. Everything runs on the phone.

## What it does

- **Today**: the heat index right now and how dangerous it is (five levels, from the US NWS heat index chart), what to do about it, and the hours of the day coloured by risk.
- **Reminders**: cups of water on a schedule that follows the heat and what you are doing. Pause for the day when you need to.
- **Hot nights**: an evening note when the night will not cool down, because that is when heat kills people in their sleep.
- **Danger days**: a full-screen alert, and a check-in for one family member who gets a text if you do not answer.
- **El Niño**: the current ENSO state from NOAA and what the next three months look like against normal, so a hot season is not a surprise.
- **Two looks**: Daylight (readable in sun) and Thermal (dark, with a thermal-camera view of the heat).

Weather comes from [Open-Meteo](https://open-meteo.com), the ENSO index from [NOAA CPC](https://www.cpc.ncep.noaa.gov/data/indices/). Location uses the system `LocationManager`; no Google Play Services.

## Build

Android Studio, or:

```
./gradlew assembleDebug
```

Kotlin, Jetpack Compose, minSdk 26. Release builds read `signing.properties` (ignored by git) for the keystore.

## Licence

GPL-3.0, see [LICENSE](LICENSE). The bundled fonts are under the SIL Open Font License, see [FONTS.md](FONTS.md).
