package com.mudhut.nudge.users.services

/**
 * Turns a User-Agent header into something a person recognises, like
 * "Chrome on macOS".
 *
 * A deliberate heuristic, not a parser. It covers the common browser and OS
 * pairs and returns [UNKNOWN] for anything else, because a wrong label on a
 * security screen is worse than an absent one — the user is being asked to spot
 * a session that is not theirs.
 *
 * Order matters in both lists: every Chromium user agent contains "Safari", and
 * Edge and Opera both contain "Chrome", so the most specific token has to win.
 */
object DeviceLabel {

    const val UNKNOWN = "Unknown device"

    /**
     * varchar(100) on the column.
     *
     * Defensive only: every label this returns is built from the fixed
     * vocabulary below and is well under the limit today. It exists so that
     * adding a long browser or platform name later cannot silently break the
     * insert.
     */
    private const val MAX_LENGTH = 100

    private val BROWSERS = listOf(
        "Edg/" to "Edge",
        "OPR/" to "Opera",
        "Firefox/" to "Firefox",
        "Chrome/" to "Chrome",
        "Safari/" to "Safari",
    )

    private val PLATFORMS = listOf(
        "iPhone" to "iPhone",
        "iPad" to "iPad",
        "Android" to "Android",
        "Mac OS X" to "macOS",
        "Windows" to "Windows",
        "Linux" to "Linux",
    )

    fun from(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return UNKNOWN

        val browser = BROWSERS.firstOrNull { userAgent.contains(it.first) }?.second
        val platform = PLATFORMS.firstOrNull { userAgent.contains(it.first) }?.second

        val label = when {
            browser != null && platform != null -> "$browser on $platform"
            // A browser with no recognised platform still helps; a platform with
            // no browser does not, and reads oddly ("on Windows").
            browser != null -> browser
            else -> UNKNOWN
        }
        return label.take(MAX_LENGTH)
    }
}
