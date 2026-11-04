package app.pwhs.blockads.data.remote.dnsleak

import kotlinx.serialization.Serializable

@Serializable
data class DnsLeakInitResponse(
    val status: String,
    val sessionId: String,
    val wsUrl: String,
    val watchWebUrl: String? = null,
    val probeDomains: List<String> = emptyList(),
    val client: DnsLeakClientInfo? = null,
    val dualStackEndpoints: DualStackEndpoints? = null,
    val stunServers: List<String> = emptyList()
)

@Serializable
data class DnsLeakClientInfo(
    val ip: String? = null,
    val country: String? = null,
    val city: String? = null,
    val region: String? = null,
    val asn: Long? = null,
    val asOrg: String? = null,
    val colo: String? = null,
    val postalCode: String? = null,
    val latitude: String? = null,
    val longitude: String? = null
)

@Serializable
data class DualStackEndpoints(
    val ipv4: String? = null,
    val ipv6: String? = null
)

@Serializable
data class DnsLeakWsEvent(
    val time: Long? = null,
    val proto: String? = null,
    val remoteIp: String? = null,
    val remotePort: Int? = null,
    val qname: String? = null,
    val qtype: String? = null,
    val isEdns0: Boolean = false,
    val udpSize: Int? = null,
    val clientSubnet: String? = null
)

@Serializable
data class DnsLeakAnalyzeRequest(
    val clientAsn: Long?,
    val resolvers: List<DnsLeakResolverItem>
)

@Serializable
data class DnsLeakResolverItem(
    val ip: String,
    val clientSubnet: String? = null
)

@Serializable
data class DnsLeakAnalyzeResponse(
    val status: String,
    val verdict: String = "secure",
    val isLeak: Boolean = false,
    val hasEcsLeak: Boolean = false,
    val message: String = "",
    val client: DnsLeakClientInfo? = null,
    val resolvers: List<DnsLeakDetectedResolver> = emptyList(),
    val detectedResolverCount: Int = 0,
    val testedAt: String? = null
)

@Serializable
data class DnsLeakDetectedResolver(
    val ip: String,
    val org: String? = null,
    val country: String? = null,
    val city: String? = null,
    val region: String? = null,
    val ptr: String? = null,
    val ns: String? = null,
    val clientSubnet: String? = null
)
