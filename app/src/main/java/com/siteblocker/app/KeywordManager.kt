package com.siteblocker.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class KeywordSort { ALPHABETICAL, RECENTLY_ADDED }

/**
 * Single source of truth for the user's blocked-keyword list.
 * Backed by SharedPreferences using a String Set, plus an ordered list
 * (JSON-encoded) so "recently added" sort order can be preserved.
 *
 * All keywords are stored already normalized (lowercase, no protocol, no www/m prefix,
 * no trailing slash) so matching at detection time is a cheap substring check.
 */
class KeywordManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Adds a keyword after normalizing and validating it. Returns true if newly added. */
    fun addKeyword(raw: String): Boolean {
        val normalized = normalize(raw)
        if (!isValid(normalized)) return false

        val current = getKeywords().toMutableSet()
        if (current.contains(normalized)) return false // duplicate

        current.add(normalized)
        persist(current)
        appendToOrder(normalized)
        Logger.i(TAG, "Keyword added: $normalized")
        return true
    }

    /** Replaces an existing keyword with a new value (used by the Edit action). */
    fun editKeyword(old: String, new: String): Boolean {
        val normalizedOld = normalize(old)
        val normalizedNew = normalize(new)
        if (!isValid(normalizedNew)) return false

        val current = getKeywords().toMutableSet()
        if (!current.contains(normalizedOld)) return false
        if (normalizedOld != normalizedNew && current.contains(normalizedNew)) return false // would duplicate

        current.remove(normalizedOld)
        current.add(normalizedNew)
        persist(current)

        val order = getOrder().toMutableList()
        val idx = order.indexOf(normalizedOld)
        if (idx >= 0) order[idx] = normalizedNew else order.add(normalizedNew)
        persistOrder(order)
        Logger.i(TAG, "Keyword edited: $normalizedOld -> $normalizedNew")
        return true
    }

    /** Removes a keyword. Returns true if it existed and was removed. */
    fun removeKeyword(raw: String): Boolean {
        val normalized = normalize(raw)
        val current = getKeywords().toMutableSet()
        val removed = current.remove(normalized)
        if (removed) {
            persist(current)
            val order = getOrder().toMutableList()
            order.remove(normalized)
            persistOrder(order)
            Logger.i(TAG, "Keyword removed: $normalized")
        }
        return removed
    }

    /** Returns all stored keywords (unordered set). */
    fun getKeywords(): Set<String> {
        return prefs.getStringSet(KEY_KEYWORDS, emptySet())?.toSet() ?: emptySet()
    }

    /** Returns keywords sorted either alphabetically or by recently-added order. */
    fun getKeywordsSorted(sort: KeywordSort = KeywordSort.ALPHABETICAL): List<String> {
        return when (sort) {
            KeywordSort.ALPHABETICAL -> getKeywords().sorted()
            KeywordSort.RECENTLY_ADDED -> {
                val order = getOrder()
                // Newest first; fall back to any keywords missing from order (legacy data).
                val ordered = order.asReversed().filter { getKeywords().contains(it) }
                val missing = getKeywords() - order.toSet()
                ordered + missing.sorted()
            }
        }
    }

    /** Case-insensitive substring search over stored keywords. */
    fun search(query: String): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return getKeywordsSorted()
        return getKeywordsSorted().filter { it.contains(q) }
    }

    /** Returns true if [candidateText] contains any stored keyword. */
    fun containsKeyword(candidateText: String): Boolean {
        val normalizedCandidate = UrlParser.normalize(candidateText)
        if (normalizedCandidate.isEmpty()) return false

        for (keyword in getKeywords()) {
            if (UrlParser.matches(normalizedCandidate, keyword)) {
                return true
            }
        }
        return false
    }

    /** Returns the first stored keyword that matches [candidateText], if any. */
    fun findMatchingKeyword(candidateText: String): String? {
        val normalizedCandidate = UrlParser.normalize(candidateText)
        if (normalizedCandidate.isEmpty()) return null
        return getKeywords().firstOrNull { UrlParser.matches(normalizedCandidate, it) }
    }

    /** Clears every stored keyword. */
    fun clear() {
        prefs.edit().remove(KEY_KEYWORDS).remove(KEY_ORDER).apply()
    }

    /** Normalizes raw keyword input the same way URLs are normalized, for consistent matching. */
    fun normalize(raw: String): String {
        return UrlParser.normalize(raw)
    }

    /** Rejects empty, too-short, or clearly invalid entries (e.g. bare protocol, whitespace-only). */
    fun isValid(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        if (normalized.length < 2) return false
        if (!normalized.any { it.isLetterOrDigit() }) return false
        return true
    }

    /** Exports all keywords as a newline-separated string. */
    fun exportAsText(): String = getKeywordsSorted().joinToString("\n")

    /** Exports all keywords as JSON, suitable for [importFromJson] / backup. */
    fun exportAsJson(): String = gson.toJson(getKeywordsSorted())

    /**
     * Imports keywords from freeform text (one per line, ignoring blanks/duplicates).
     * Returns the number of keywords actually added.
     */
    fun importFromText(text: String): Int {
        var added = 0
        text.lineSequence().forEach { line ->
            if (line.isNotBlank() && addKeyword(line)) added++
        }
        return added
    }

    /** Imports keywords from a JSON array string (as produced by [exportAsJson]). */
    fun importFromJson(json: String): Int {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String> = gson.fromJson(json, type) ?: emptyList()
            var added = 0
            list.forEach { if (addKeyword(it)) added++ }
            added
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to import keywords from JSON", e)
            0
        }
    }

    /** Full backup payload: keywords + order, used by Settings > Backup Keywords. */
    fun backup(): String {
        val payload = mapOf(
            "keywords" to getKeywords().toList(),
            "order" to getOrder()
        )
        return gson.toJson(payload)
    }

    /** Restores a backup produced by [backup]. Replaces the current list entirely. */
    fun restore(json: String): Boolean {
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            val payload: Map<String, List<String>> = gson.fromJson(json, type) ?: return false
            val keywords = payload["keywords"]?.toSet() ?: return false
            val order = payload["order"] ?: keywords.toList()
            persist(keywords)
            persistOrder(order)
            Logger.i(TAG, "Keywords restored from backup (${keywords.size} entries)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to restore keyword backup", e)
            false
        }
    }

    private fun getOrder(): List<String> {
        val json = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun appendToOrder(keyword: String) {
        val order = getOrder().toMutableList()
        order.remove(keyword)
        order.add(keyword)
        persistOrder(order)
    }

    private fun persist(keywords: Set<String>) {
        prefs.edit().putStringSet(KEY_KEYWORDS, keywords).apply()
    }

    private fun persistOrder(order: List<String>) {
        prefs.edit().putString(KEY_ORDER, gson.toJson(order)).apply()
    }

    companion object {
        private const val TAG = "KeywordManager"
        private const val PREFS_NAME = "site_blocker_prefs"
        private const val KEY_KEYWORDS = "blocked_keywords"
        private const val KEY_ORDER = "blocked_keywords_order"
    }
}
