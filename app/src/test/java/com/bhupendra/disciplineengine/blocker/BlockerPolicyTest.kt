package com.bhupendra.disciplineengine.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockerPolicyTest {

    // Verifies 400KB Bloom filter blocks adult hosts while allowing educational domains
    @Test
    fun bloomFilterBlocksAdultUrlsAndAllowsStudySites() {
        val filter = AdultUrlBloomFilter()
        assertTrue(filter.isBlockedUrl("https://www.pornhub.com/watch"))
        assertTrue(filter.isBlockedUrl("https://m.xvideos.com"))
        assertFalse(filter.isBlockedUrl("https://www.pw.live/study/batches"))
        assertFalse(filter.isBlockedUrl("https://ncert.nic.in/textbook.php"))
        assertTrue(filter.requiresSafeSearchEnforcement("https://www.google.com/search?q=physics"))
        assertFalse(filter.requiresSafeSearchEnforcement("https://www.google.com/search?q=physics&safe=active"))
    }

    // Verifies Private DNS enforcement requires both hostname mode and safe family host
    @Test
    fun privateDnsRequiresHostnameModeAndSafeHost() {
        assertTrue(PrivateDnsGuard.isValidConfig("hostname", "family.cloudflare-dns.com"))
        assertTrue(PrivateDnsGuard.isValidConfig("hostname", "1.1.1.3"))
        assertFalse(PrivateDnsGuard.isValidConfig("off", "family.cloudflare-dns.com"))
        assertFalse(PrivateDnsGuard.isValidConfig("hostname", "dns.google"))
    }

    // Verifies Hard Lock, Illness Lock, and Dopamine Debt override Reels Time Bank
    @Test
    fun shortFormPolicyEnforcesLocksAndDebt() {
        assertEquals(
            BlockReason.ILLNESS_MODE_LOCK,
            BlockerPolicyEngine.evaluateShortFormAccess(true, false, 30, 0)
        )
        assertEquals(
            BlockReason.REELS_SHORTS_HARD_LOCK,
            BlockerPolicyEngine.evaluateShortFormAccess(false, true, 30, 0)
        )
        assertEquals(
            BlockReason.REELS_BANK_EMPTY,
            BlockerPolicyEngine.evaluateShortFormAccess(false, false, 15, 30)
        )
        assertEquals(
            BlockReason.ALLOW,
            BlockerPolicyEngine.evaluateShortFormAccess(false, false, 15, 0)
        )
    }

    // Verifies Settings tamper guard catches Force Stop / Uninstall unless ethically unlocked
    @Test
    fun settingsGuardDetectsTamperKeywords() {
        assertTrue(BlockerPolicyEngine.isSettingsTamperDetected(listOf("Discipline Engine", "Force stop"), false))
        assertFalse(BlockerPolicyEngine.isSettingsTamperDetected(listOf("Display", "Dark theme"), false))
        assertFalse(BlockerPolicyEngine.isSettingsTamperDetected(listOf("Discipline Engine", "Force stop"), true))
    }
}
