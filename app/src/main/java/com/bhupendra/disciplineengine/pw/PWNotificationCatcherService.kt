package com.bhupendra.disciplineengine.pw

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bhupendra.disciplineengine.data.DisciplineDatabase
import com.bhupendra.disciplineengine.data.PwNoticeTestEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Passively captures Physics Wallah notifications from status bar with zero API scraping
class PWNotificationCatcherService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Extracts and stores official PW push notifications into Room database
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName != PW_PACKAGE) return

        val extras = posted.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        val parsed = PwNoticeParser.parse(title, text)
        val itemId = "pw_notif_${posted.postTime}_${parsed.title.hashCode()}"
        val entity = PwNoticeTestEntity(
            itemId = itemId,
            itemType = parsed.itemType.name,
            title = parsed.title,
            rawNotificationText = parsed.body,
            timestampEpochMillis = posted.postTime,
            requiredChapterIdsJson = "",
            isTestUnlocked = parsed.itemType != PwItemType.TEST_SCHEDULED
        )

        serviceScope.launch {
            runCatching {
                DisciplineDatabase.get(applicationContext).dao().upsertPwItem(entity)
            }
        }
    }

    // Cancels listener coroutine scope on service destruction
    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PW_PACKAGE = "xyz.penpencil.physicswallah"
    }
}
