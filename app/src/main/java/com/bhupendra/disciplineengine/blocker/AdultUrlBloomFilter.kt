package com.bhupendra.disciplineengine.blocker

import java.util.BitSet

// Lightweight ~400KB in-memory Bloom Filter + safe search guard for browser URL bars
class AdultUrlBloomFilter {

    private val bitSet = BitSet(BIT_COUNT)

    init {
        SEEDED_BLOCKED_TOKENS.forEach { add(it) }
    }

    // Inserts a normalized domain or token into the 400KB BitSet
    fun add(token: String) {
        val clean = normalizeHost(token)
        if (clean.isEmpty()) return
        for (seed in HASH_SEEDS) {
            bitSet.set(hashIndex(clean, seed))
        }
    }

    // Checks whether a URL or host hits the Bloom Filter or unsafe patterns
    fun isBlockedUrl(rawUrl: String): Boolean {
        val normalized = rawUrl.lowercase().trim()
        if (normalized.isEmpty()) return false
        val host = normalizeHost(normalized)
        val segments = host.split(".")
        for (i in segments.indices) {
            val subDomain = segments.drop(i).joinToString(".")
            if (mightContainExact(subDomain)) return true
        }
        return BLOCKED_SUBSTRINGS.any { normalized.contains(it) }
    }

    // Checks if a search URL needs &safe=active enforced
    fun requiresSafeSearchEnforcement(rawUrl: String): Boolean {
        val lower = rawUrl.lowercase()
        val isSearchEngine = lower.contains("google.com/search") ||
            lower.contains("bing.com/search") ||
            lower.contains("duckduckgo.com/")
        return isSearchEngine && !lower.contains("safe=active")
    }

    // Checks all four Bloom hash bit positions for a candidate token
    private fun mightContainExact(token: String): Boolean {
        return HASH_SEEDS.all { seed -> bitSet.get(hashIndex(token, seed)) }
    }

    // Extracts clean lowercase domain host from raw URL bar text
    private fun normalizeHost(raw: String): String {
        return raw.lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore(":")
            .trim()
    }

    // Computes a non-negative BitSet index using FNV-1a mixing
    private fun hashIndex(value: String, seed: Int): Int {
        var h = -2128831035 xor seed
        for (ch in value) {
            h = h xor ch.code
            h *= 16777619
        }
        return (h and Int.MAX_VALUE) % BIT_COUNT
    }

    companion object {
        // 3,276,800 bits = 409,600 bytes (~400 KB) per blueprint memory budget
        const val BIT_COUNT = 400 * 1024 * 8
        private val HASH_SEEDS = intArrayOf(0x13579BDF, 0x2468ACE0, 0x5A5A5A5A, 0x7F3A19C5)

        // Compact starter domain signatures (extensible from assets without extra RAM)
        private val SEEDED_BLOCKED_TOKENS = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
            "youporn.com", "spankbang.com", "brazzers.com", "onlyfans.com", "chaturbate.com",
            "stripchat.com", "bongacams.com", "rule34.xxx", "nhentai.net", "hanime.tv"
        )

        private val BLOCKED_SUBSTRINGS = listOf(
            "porn", "xxx", "hentai", "nsfw", "xvideos", "xnxx", "chaturbate"
        )
    }
}
