package com.bhupendra.disciplineengine.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.bhupendra.disciplineengine.blocker.DisciplineDeviceAdminReceiver

// Helper for Xiaomi HyperOS / POCO M6 5G autostart, battery, restricted settings, and Device Admin
object HyperOsOnboardingHelper {

    // Detects Xiaomi, POCO, or Redmi devices running HyperOS or MIUI
    fun isXiaomiOrPocoDevice(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND
    ): Boolean {
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("POCO", ignoreCase = true) ||
            brand.equals("POCO", ignoreCase = true) ||
            brand.equals("Redmi", ignoreCase = true)
    }

    // Checks whether Device Admin anti-uninstall shield is active
    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val adminComponent = ComponentName(context, DisciplineDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    // Checks whether the app is exempt from Android battery Doze optimizations
    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Launches Device Admin activation prompt
    fun launchDeviceAdminPrompt(context: Context) {
        val adminComponent = ComponentName(context, DisciplineDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Protects Discipline Engine from impulsive uninstallation during study locks."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, intent)
    }

    // Opens Xiaomi/POCO Autostart settings with App Info fallback
    fun openHyperOsAutostart(context: Context) {
        val miuiIntent = Intent("miui.intent.action.OP_AUTO_START").apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!startSafely(context, miuiIntent)) {
            val componentIntent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (!startSafely(context, componentIntent)) {
                openAppDetailsSettings(context)
            }
        }
    }

    // Opens Battery optimization exclusion or HyperOS Background Permissions
    fun openBatteryNoRestrictions(context: Context) {
        val ignoreDoze = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!startSafely(context, ignoreDoze)) {
            openAppDetailsSettings(context)
        }
    }

    // Opens Xiaomi Other Permissions (Display pop-up windows in background) or App Info
    fun openHyperOsPopupPermissions(context: Context) {
        val permEditor = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!startSafely(context, permEditor)) {
            openAppDetailsSettings(context)
        }
    }

    // Opens App Info page (also used on Android 13+ for 'Allow restricted settings')
    fun openAppDetailsSettings(context: Context) {
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startSafely(context, details)
    }

    // Launches an Android settings intent safely without crashing on vendor ROM variations
    private fun startSafely(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
