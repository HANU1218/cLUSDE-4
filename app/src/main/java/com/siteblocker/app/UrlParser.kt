package com.siteblocker.app

/**
 * Utility responsible for normalizing raw URL / address-bar text so it can be
 * reliably matched against user-defined keywords.
 *
 * Normalization rules:
 *  - lower-cased
 *  - protocol (http://, https://) stripped
 *  - leading "www." stripped
 *  - leading "m." (mobile subdomain) stripped
 *  - surrounding whitespace trimmed
 */
object UrlParser {

    private val PROTOCOL_REGEX = Regex("^[a-zA-Z]+://")
    private val WWW_REGEX = Regex("^www\\.")
    private val MOBILE_REGEX = Regex("^m\\.")
    private val TRAILING_SLASH_REGEX = Regex("/+$")

    /**
     * Normalizes a raw string coming from an address bar, page title, or any
     * accessibility node text so it can be safely compared against keywords.
     * Strips protocol, "www."/"m." prefixes, trailing slashes, and case.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        var result = raw.trim().lowercase()
        result = PROTOCOL_REGEX.replace(result, "")
        result = WWW_REGEX.replace(result, "")
        result = MOBILE_REGEX.replace(result, "")
        result = TRAILING_SLASH_REGEX.replace(result, "")

        return result.trim()
    }

    /**
     * Returns true if [normalizedKeyword] appears anywhere inside [normalizedText].
     * Both inputs are expected to already be normalized via [normalize].
     * Uses simple, safe partial (substring) matching.
     */
    fun matches(normalizedText: String, normalizedKeyword: String): Boolean {
        if (normalizedText.isEmpty() || normalizedKeyword.isEmpty()) return false
        return normalizedText.contains(normalizedKeyword)
    }

    /**
     * Extracts the best-guess "URL-like" candidate text from a piece of raw
     * accessibility text (address bar content usually comes bundled with
     * extra characters like padlock icons or suggestion text on some browsers).
     * This is intentionally conservative: it only trims whitespace, since browsers
     * vary wildly in how they expose the address bar node.
     */
    fun extractCandidate(raw: CharSequence?): String {
        return raw?.toString()?.trim().orEmpty()
    }
}
