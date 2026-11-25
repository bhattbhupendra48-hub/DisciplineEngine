package com.bhupendra.disciplineengine.blocker

import android.content.Context
import android.provider.Settings

// Verifies OS-level Private DNS configuration without running any VPN service
object PrivateDnsGuard {

    private val ALLOWED_HOSTS = setOf(
        "family.cloudflare-dns.com",
        "1.1.1.3",
        "family.adguard-dns.com",
        "dns-family.adguard.com"
    )

    // Checks both Private DNS mode (hostname) and specifier against family-safe providers
    fun isPrivateDnsEnforced(context: Context): Boolean {
        return try {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
            val specifier = Settings.Global.getString(context.contentResolver, "private_dns_specifier")
            isValidConfig(mode, specifier)
        } catch (_: Exception) {
            false
        }
    }

    // Pure helper validating Private DNS mode and host specifier
    fun isValidConfig(mode: String?, specifier: String?): Boolean {
        if (!mode.equals("hostname", ignoreCase = true)) return false
        val cleanHost = specifier?.trim()?.lowercase() ?: return false
        return cleanHost in ALLOWED_HOSTS
    }

    // Returns current Private DNS host string for display in Reel Shield status
    fun currentSpecifier(context: Context): String {
        return runCatching {
            val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "off"
            val spec = Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: "unset"
            "$mode ($spec)"
        }.getOrDefault("unknown")
    }
}
