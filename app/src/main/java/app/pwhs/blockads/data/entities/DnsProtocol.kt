package app.pwhs.blockads.data.entities

enum class DnsProtocol {
    PLAIN,  // Traditional UDP DNS on port 53
    DOH,    // DNS-over-HTTPS (RFC 8484)
    DOH3,   // DNS-over-HTTP/3 (RFC 9114 over QUIC)
    DOT,    // DNS-over-TLS (RFC 7858)
    DOQ,    // DNS-over-QUIC (RFC 9250)
    ODOH    // Oblivious DoH (RFC 9230)
}