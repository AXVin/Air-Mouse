package com.example.airmouse

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user settings and theme preferences persisted via SharedPreferences.
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("air_mouse_prefs", Context.MODE_PRIVATE)

    var theme: AppTheme
        get() {
            val name = prefs.getString("app_theme", AppTheme.MONET.name) ?: AppTheme.MONET.name
            return try {
                AppTheme.valueOf(name)
            } catch (_: Exception) {
                AppTheme.MONET
            }
        }
        set(value) = prefs.edit().putString("app_theme", value.name).apply()

    var amoledBlack: Boolean
        get() = prefs.getBoolean("amoled_black", false)
        set(value) = prefs.edit().putBoolean("amoled_black", value).apply()

    var twoFingerVertScroll: Boolean
        get() = prefs.getBoolean("two_vert_scroll", true)
        set(value) = prefs.edit().putBoolean("two_vert_scroll", value).apply()

    var twoFingerHorizScroll: Boolean
        get() = prefs.getBoolean("two_horiz_scroll", true)
        set(value) = prefs.edit().putBoolean("two_horiz_scroll", value).apply()

    var twoFingerTapRightClick: Boolean
        get() = prefs.getBoolean("two_tap_right", true)
        set(value) = prefs.edit().putBoolean("two_tap_right", value).apply()

    var pinchToZoom: Boolean
        get() = prefs.getBoolean("pinch_zoom", true)
        set(value) = prefs.edit().putBoolean("pinch_zoom", value).apply()

    var threeFingerSwipeNav: Boolean
        get() = prefs.getBoolean("three_swipe_nav", true)
        set(value) = prefs.edit().putBoolean("three_swipe_nav", value).apply()

    var volumeButtonsAsMouse: Boolean
        get() = prefs.getBoolean("vol_btns_mouse", true)
        set(value) = prefs.edit().putBoolean("vol_btns_mouse", value).apply()

    var invertVolumeButtons: Boolean
        get() = prefs.getBoolean("invert_vol_btns", false)
        set(value) = prefs.edit().putBoolean("invert_vol_btns", value).apply()
}
