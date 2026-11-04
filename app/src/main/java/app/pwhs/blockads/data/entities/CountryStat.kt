package app.pwhs.blockads.data.entities

/**
 * Aggregated DNS query count by destination country.
 */
data class CountryStat(
    val countryCode: String,
    val count: Int
)
