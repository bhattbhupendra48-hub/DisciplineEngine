package com.bhupendra.disciplineengine.blocker

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

// Prevents casual launcher or Play Store uninstallation while anti-tamper lock is active
class DisciplineDeviceAdminReceiver : DeviceAdminReceiver() {

    // Confirms anti-uninstall Device Admin shield activation
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    // Displays warning when user attempts to revoke Device Admin protection
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Discipline Engine Shield is active. Disabling Device Admin drops your anti-uninstall guard."
    }
}
