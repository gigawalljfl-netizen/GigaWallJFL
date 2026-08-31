package com.gigawalljfl.geolock.util

import android.content.Context

/**
 * Flags simples e não sensíveis (nunca segredos) persistidas via
 * SharedPreferences — se já viu o onboarding, se o bloqueio do app por
 * PIN/biometria está ativado.
 */
object AppPreferences {
    private const val PREFS_NAME = "geolock_prefs"
    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeenOnboarding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(context: Context) {
        prefs(context).edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
    }

    fun isAppLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }
}
