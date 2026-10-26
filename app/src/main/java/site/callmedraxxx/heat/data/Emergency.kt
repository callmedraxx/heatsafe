package site.callmedraxxx.heat.data

/**
 * The number to call for an ambulance, by country code. 112 works from most mobile
 * phones worldwide, so it is the fallback. Only numbers we are sure of are listed;
 * corrections are welcome as pull requests.
 */
object Emergency {
    private val numbers = mapOf(
        "US" to "911", "CA" to "911", "MX" to "911", "PH" to "911", "AR" to "107", "BR" to "192",
        "GB" to "999", "IE" to "112", "AU" to "000", "NZ" to "111", "IN" to "112", "BD" to "999",
        "PK" to "1122", "ID" to "112", "MY" to "999", "SG" to "995", "TH" to "1669", "VN" to "115",
        "CN" to "120", "JP" to "119", "KR" to "119", "NG" to "112", "GH" to "112", "KE" to "999",
        "ZA" to "10177", "EG" to "123", "SA" to "997", "AE" to "998", "QA" to "999", "KW" to "112",
        "TR" to "112", "IR" to "115", "IQ" to "122",
    )

    fun number(countryCode: String): String = numbers[countryCode.uppercase()] ?: "112"
}
