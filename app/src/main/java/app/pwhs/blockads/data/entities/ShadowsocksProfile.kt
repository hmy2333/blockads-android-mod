package app.pwhs.blockads.data.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ShadowsocksProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val method: String,
    val password: String,
    val server: String,
    val port: Int,
    val tag: String = "",
    val rawUri: String = ""
) {
    fun toJson(): String = jsonInstance.encodeToString(serializer(), this)

    val displayName: String
        get() = name.ifEmpty { tag.ifEmpty { "$server:$port" } }

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): ShadowsocksProfile =
            jsonInstance.decodeFromString(serializer(), jsonStr)
    }
}

@Serializable
data class ShadowsocksProfileList(
    val profiles: List<ShadowsocksProfile> = emptyList(),
) {
    fun toJson(): String = jsonInstance.encodeToString(serializer(), this)

    companion object {
        private val jsonInstance = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): ShadowsocksProfileList =
            try {
                jsonInstance.decodeFromString(serializer(), jsonStr)
            } catch (_: Exception) {
                ShadowsocksProfileList()
            }
    }
}
