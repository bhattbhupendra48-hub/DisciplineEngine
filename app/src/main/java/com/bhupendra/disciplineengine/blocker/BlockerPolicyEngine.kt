package com.bhupendra.disciplineengine.blocker

// Defines why the blocker intercepted the foreground screen
enum class BlockReason {
    ALLOW,
    REELS_SHORTS_HARD_LOCK,
    REELS_BANK_EMPTY,
    ILLNESS_MODE_LOCK,
    ADULT_URL_BLOCKED,
    PRIVATE_DNS_DISABLED_BROWSER_LOCK,
    SETTINGS_ANTI_TAMPER,
    FLOATING_WINDOW_BYPASS
}

// Pure policy evaluator for Reels/Shorts, browsers, and Settings anti-tamper
object BlockerPolicyEngine {

    val REELS_SHORTS_VIEW_IDS = setOf(
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/reel_recycler",
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/reel_viewer_root",
        "com.snapchat.android:id/spotlight_view_pager"
    )

    val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.mi.globalbrowser",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx"
    )

    val BROWSER_URL_BAR_IDS = setOf(
        "com.android.chrome:id/url_bar",
        "com.brave.browser:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.mi.globalbrowser:id/url",
        "url_bar"
    )

    private val SETTINGS_GUARD_KEYWORDS = listOf(
        "discipline engine",
        "force stop",
        "uninstall",
        "clear storage",
        "clear data",
        "device admin",
        "private dns",
        "accessibility"
    )

    // Evaluates whether a short-form feed should be blocked right now
    fun evaluateShortFormAccess(
        isIllnessHardLockActive: Boolean,
        isStudyOrDangerHardLock: Boolean,
        reelsTimeBankMinutes: Int,
        dopamineDebtPushups: Int
    ): BlockReason {
        if (isIllnessHardLockActive) return BlockReason.ILLNESS_MODE_LOCK
        if (isStudyOrDangerHardLock) return BlockReason.REELS_SHORTS_HARD_LOCK
        if (dopamineDebtPushups > 0 || reelsTimeBankMinutes <= 0) return BlockReason.REELS_BANK_EMPTY
        return BlockReason.ALLOW
    }

    // Evaluates browser access using Private DNS enforcement and 400KB Bloom Filter
    fun evaluateBrowserAccess(
        isPrivateDnsValid: Boolean,
        visibleUrlText: String?,
        bloomFilter: AdultUrlBloomFilter
    ): BlockReason {
        if (!isPrivateDnsValid) return BlockReason.PRIVATE_DNS_DISABLED_BROWSER_LOCK
        if (!visibleUrlText.isNullOrBlank() && bloomFilter.isBlockedUrl(visibleUrlText)) {
            return BlockReason.ADULT_URL_BLOCKED
        }
        return BlockReason.ALLOW
    }

    // Checks whether visible Settings text indicates tampering with lock permissions
    fun isSettingsTamperDetected(
        visibleSettingsStrings: List<String>,
        ethicalUnlockActive: Boolean
    ): Boolean {
        if (ethicalUnlockActive) return false
        return visibleSettingsStrings.any { text ->
            val lower = text.lowercase()
            SETTINGS_GUARD_KEYWORDS.any { keyword -> lower.contains(keyword) }
        }
    }
}
