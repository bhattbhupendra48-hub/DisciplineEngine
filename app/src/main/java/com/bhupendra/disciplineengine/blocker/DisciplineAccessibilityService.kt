package com.bhupendra.disciplineengine.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.bhupendra.disciplineengine.alarm.AlarmVerifyActivity
import com.bhupendra.disciplineengine.alarm.DirectBootAlarmStore
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.ScheduleProfileEntity
import com.bhupendra.disciplineengine.domain.ScheduleEngine
import com.bhupendra.disciplineengine.domain.StreakEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

// Event-driven AccessibilityService sleeping at 0% CPU until monitored apps emit events
class DisciplineAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var bloomFilter: AdultUrlBloomFilter
    private lateinit var blockerStore: BlockerStateStore
    private lateinit var alarmStore: DirectBootAlarmStore

    @Volatile private var cachedHardLock: Boolean = false
    @Volatile private var cachedIllnessLock: Boolean = false
    @Volatile private var cachedReelsBankMinutes: Int = 0
    @Volatile private var cachedDopamineDebt: Int = 0
    @Volatile private var cachedP1TaskTitle: String = "P1 Core Deep Study Block"
    @Volatile private var lastBankDebitMs: Long = 0L
    @Volatile private var lastDnsCheckMs: Long = 0L
    @Volatile private var cachedDnsSafe: Boolean = true
    @Volatile private var lastInterceptMs: Long = 0L

    // Initializes lightweight 400KB Bloom filter and preference stores
    override fun onServiceConnected() {
        super.onServiceConnected()
        bloomFilter = AdultUrlBloomFilter()
        blockerStore = BlockerStateStore(this)
        alarmStore = DirectBootAlarmStore(this)
        refreshCachedPolicy()
    }

    // Inspects view hierarchy only for declared packages without background polling
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        // Layer 1 Guard: Keep Kill-Proof Alarm in foreground if actively ringing
        if (alarmStore.isRingingActive() && pkg != packageName) {
            val alarmIntent = Intent(this, AlarmVerifyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(alarmIntent)
            return
        }

        // Layer 2 Guard: Collapse split-screen or HyperOS floating windows on monitored packages
        if (hasSplitOrFloatingBypass()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val root = rootInActiveWindow ?: return
        refreshCachedPolicy()

        when (pkg) {
            "com.android.settings" -> handleSettingsGuard(root)
            "com.google.android.youtube",
            "com.instagram.android",
            "com.snapchat.android" -> handleShortFormGuard(root)
            in BlockerPolicyEngine.BROWSER_PACKAGES -> handleBrowserGuard(root)
            "xyz.penpencil.physicswallah" -> handlePassivePwTracking(root)
        }
    }

    // Unused AccessibilityService callback
    override fun onInterrupt() = Unit

    // Cancels coroutine scope on service shutdown
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // Intercepts Settings tampering with Force Stop, Uninstall, Accessibility, or Private DNS
    private fun handleSettingsGuard(root: AccessibilityNodeInfo) {
        val isLockActive = cachedHardLock || cachedIllnessLock || cachedDopamineDebt > 0 || alarmStore.isRingingActive()
        if (!isLockActive && blockerStore.isEthicalSettingsUnlockActive()) return

        val collectedText = mutableListOf<String>()
        collectVisibleTexts(root, collectedText, maxNodes = 40)
        if (BlockerPolicyEngine.isSettingsTamperDetected(collectedText, blockerStore.isEthicalSettingsUnlockActive())) {
            triggerBlock(BlockReason.SETTINGS_ANTI_TAMPER)
        }
    }

    // Blocks YouTube Shorts, Instagram Reels, and Snapchat Spotlight view IDs surgically
    private fun handleShortFormGuard(root: AccessibilityNodeInfo) {
        val shortFormActive = BlockerPolicyEngine.REELS_SHORTS_VIEW_IDS.any { viewId ->
            root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        }
        if (!shortFormActive) return

        val decision = BlockerPolicyEngine.evaluateShortFormAccess(
            isIllnessHardLockActive = cachedIllnessLock,
            isStudyOrDangerHardLock = cachedHardLock,
            reelsTimeBankMinutes = cachedReelsBankMinutes,
            dopamineDebtPushups = cachedDopamineDebt
        )

        if (decision != BlockReason.ALLOW) {
            triggerBlock(decision)
            return
        }

        // During free hours with positive Reels Bank, deduct 1 min per 60s watched
        val now = System.currentTimeMillis()
        if (lastBankDebitMs == 0L) {
            lastBankDebitMs = now
        } else if (now - lastBankDebitMs >= 60_000L) {
            lastBankDebitMs = now
            debitOneReelsBankMinute()
        }
    }

    // Verifies OS Private DNS every 5 minutes and checks browser URL bar against Bloom filter
    private fun handleBrowserGuard(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastDnsCheckMs >= 300_000L || !cachedDnsSafe) {
            lastDnsCheckMs = now
            cachedDnsSafe = PrivateDnsGuard.isPrivateDnsEnforced(this)
        }

        val urlText = extractBrowserUrl(root)
        val decision = BlockerPolicyEngine.evaluateBrowserAccess(
            isPrivateDnsValid = cachedDnsSafe,
            visibleUrlText = urlText,
            bloomFilter = bloomFilter
        )
        if (decision != BlockReason.ALLOW) {
            triggerBlock(decision)
            return
        }

        if (urlText != null && bloomFilter.requiresSafeSearchEnforcement(urlText)) {
            triggerBlock(BlockReason.ADULT_URL_BLOCKED)
        }
    }

    // Passively records PW study activity when user opens Physics Wallah (hooked in Phase 7)
    private fun handlePassivePwTracking(root: AccessibilityNodeInfo) {
        val texts = mutableListOf<String>()
        collectVisibleTexts(root, texts, maxNodes = 25)
        if (texts.any { it.contains("lecture", ignoreCase = true) || it.contains("dpp", ignoreCase = true) }) {
            // Phase 7 passive screen sync observer reads completion state from preferences
            getSharedPreferences("pw_passive_sync", MODE_PRIVATE)
                .edit()
                .putLong("last_pw_lecture_seen_ms", System.currentTimeMillis())
                .apply()
        }
    }

    // Executes GLOBAL_ACTION_BACK and opens the glassmorphic interception shield
    private fun triggerBlock(reason: BlockReason) {
        val now = System.currentTimeMillis()
        if (now - lastInterceptMs < 700L) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        lastInterceptMs = now
        performGlobalAction(GLOBAL_ACTION_BACK)

        val blockedCount = blockerStore.incrementBlockedToday()
        val shieldIntent = Intent(this, BlockerShieldActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockerShieldActivity.EXTRA_REASON, reason.name)
            putExtra(BlockerShieldActivity.EXTRA_P1_TASK, cachedP1TaskTitle)
            putExtra(BlockerShieldActivity.EXTRA_BLOCKED_COUNT, blockedCount)
            putExtra(BlockerShieldActivity.EXTRA_DOPAMINE_DEBT, cachedDopamineDebt)
            putExtra(BlockerShieldActivity.EXTRA_ALLOW_PUSHUP_UNLOCK, !cachedHardLock && !cachedIllnessLock)
        }
        startActivity(shieldIntent)
    }

    // Detects Android split-screen dividers or HyperOS freeform floating windows
    private fun hasSplitOrFloatingBypass(): Boolean {
        val activeWindows = windows ?: return false
        if (activeWindows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }) {
            return true
        }
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels.toLong()
        return activeWindows.any { win ->
            if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@any false
            val bounds = Rect()
            win.getBoundsInScreen(bounds)
            val area = bounds.width().toLong() * bounds.height().toLong()
            val isFloatingSize = area in 1L..(screenArea * 70L / 100L)
            val pkg = win.root?.packageName?.toString()
            isFloatingSize && pkg != null && pkg != packageName
        }
    }

    // Extracts visible URL text from common Chrome/Firefox/Mi Browser URL bars
    private fun extractBrowserUrl(root: AccessibilityNodeInfo): String? {
        for (id in BlockerPolicyEngine.BROWSER_URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val text = nodes.firstOrNull()?.text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    // Collects visible node text bounded by maxNodes to stay well within <5MB RAM budget
    private fun collectVisibleTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, maxNodes: Int) {
        if (node == null || out.size >= maxNodes) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            if (out.size >= maxNodes) break
            collectVisibleTexts(node.getChild(i), out, maxNodes)
        }
    }

    // Refreshes cached Room schedule and streak policy asynchronously
    private fun refreshCachedPolicy() {
        serviceScope.launch {
            runCatching {
                val dao = DisciplineDatabase.get(applicationContext).dao()
                val streak = dao.getStreak()
                val profile = dao.getScheduleProfile() ?: ScheduleProfileEntity()
                val todayEpoch = LocalDate.now().toEpochDay()
                val blocks = dao.getScheduleForDay(todayEpoch)
                val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }

                cachedIllnessLock = streak != null && StreakEngine.isIllnessHardLockActive(streak, todayEpoch)
                cachedReelsBankMinutes = streak?.reelsTimeBankMinutes ?: 0
                cachedDopamineDebt = streak?.dopamineDebtPushups ?: 0
                cachedHardLock = ScheduleEngine.isHardLockWindowActive(profile, blocks, nowMin)
                cachedP1TaskTitle = blocks.firstOrNull {
                    it.priorityTier == "P1_CORE_NON_NEGOTIABLE" && it.status != "COMPLETED"
                }?.title ?: "Finish Today's P1 Deep Study Block"
            }
        }
    }

    // Deducts 1 minute from Reels Time Bank inside Room database
    private fun debitOneReelsBankMinute() {
        serviceScope.launch {
            runCatching {
                val dao = DisciplineDatabase.get(applicationContext).dao()
                val streak = dao.getStreak() ?: return@launch
                val next = (streak.reelsTimeBankMinutes - 1).coerceAtLeast(0)
                cachedReelsBankMinutes = next
                dao.upsertStreak(streak.copy(reelsTimeBankMinutes = next))
            }
        }
    }
}
