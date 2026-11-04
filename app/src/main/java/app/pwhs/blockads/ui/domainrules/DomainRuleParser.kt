package app.pwhs.blockads.ui.domainrules

object DomainRuleParser {
    private val IP_HOSTS_REGEX = Regex("^(?:127\\.0\\.0\\.1|0\\.0\\.0\\.0|::1|::)\\s+(\\S+)")
    private val DOMAIN_REGEX = Regex("^(?:\\*\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

    fun parseDomains(content: String): List<String> {
        val result = linkedSetOf<String>()
        content.lineSequence().forEach { rawLine ->
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                return@forEach
            }
            // Strip inline comments
            val commentIdx = line.indexOfAny(charArrayOf('#', '!'))
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx).trim()
            }
            if (line.isEmpty()) return@forEach

            // Check hosts format
            val hostsMatch = IP_HOSTS_REGEX.find(line)
            var candidate = if (hostsMatch != null) {
                hostsMatch.groupValues[1]
            } else {
                line
            }

            // Strip Adblock syntax ||...^ or @@||...^
            if (candidate.startsWith("@@||")) candidate = candidate.removePrefix("@@||")
            else if (candidate.startsWith("||")) candidate = candidate.removePrefix("||")
            if (candidate.endsWith("^")) candidate = candidate.removeSuffix("^")

            // Strip protocol and port/path if any
            if (candidate.startsWith("http://")) candidate = candidate.removePrefix("http://")
            if (candidate.startsWith("https://")) candidate = candidate.removePrefix("https://")
            val slashIdx = candidate.indexOf('/')
            if (slashIdx != -1) candidate = candidate.substring(0, slashIdx)
            val colonIdx = candidate.indexOf(':')
            if (colonIdx != -1) candidate = candidate.substring(0, colonIdx)

            candidate = candidate.trim().lowercase()
            if (candidate.isNotEmpty() &&
                candidate != "localhost" &&
                candidate != "local" &&
                candidate != "broadcasthost" &&
                candidate.any { it.isLetter() } &&
                DOMAIN_REGEX.matches(candidate)
            ) {
                result.add(candidate)
            }
        }
        return result.toList()
    }
}
