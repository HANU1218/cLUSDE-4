package com.siteblocker.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlParserTest {

    @Test
    fun `normalize strips protocol`() {
        assertEquals("youtube.com", UrlParser.normalize("https://youtube.com"))
        assertEquals("youtube.com", UrlParser.normalize("http://youtube.com"))
    }

    @Test
    fun `normalize strips www prefix`() {
        assertEquals("youtube.com", UrlParser.normalize("www.youtube.com"))
    }

    @Test
    fun `normalize strips mobile prefix`() {
        assertEquals("youtube.com", UrlParser.normalize("m.youtube.com"))
    }

    @Test
    fun `normalize combines protocol and www`() {
        assertEquals("youtube.com", UrlParser.normalize("https://www.youtube.com"))
    }

    @Test
    fun `normalize is case insensitive`() {
        assertEquals("youtube.com", UrlParser.normalize("HTTPS://WWW.YOUTUBE.COM"))
    }

    @Test
    fun `normalize handles blank and null input`() {
        assertEquals("", UrlParser.normalize(null))
        assertEquals("", UrlParser.normalize("   "))
    }

    @Test
    fun `matches performs partial substring match`() {
        val normalizedPage = UrlParser.normalize("https://www.youtube.com/watch?v=abc123")
        assertTrue(UrlParser.matches(normalizedPage, "youtube.com"))
        assertTrue(UrlParser.matches(normalizedPage, "youtube"))
        assertFalse(UrlParser.matches(normalizedPage, "facebook"))
    }

    @Test
    fun `matches ignores protocol and www on both sides`() {
        val cases = listOf(
            "youtube.com",
            "https://youtube.com",
            "www.youtube.com",
            "m.youtube.com"
        )
        val keyword = UrlParser.normalize("youtube.com")

        for (case in cases) {
            val normalized = UrlParser.normalize(case)
            assertTrue("Failed for case: $case", UrlParser.matches(normalized, keyword))
        }
    }

    @Test
    fun `empty keyword never matches`() {
        val normalizedPage = UrlParser.normalize("https://youtube.com")
        assertFalse(UrlParser.matches(normalizedPage, ""))
    }

    @Test
    fun `normalize strips trailing slash`() {
        assertEquals("youtube.com", UrlParser.normalize("https://youtube.com/"))
        assertEquals("youtube.com/watch", UrlParser.normalize("https://youtube.com/watch/"))
    }
}
