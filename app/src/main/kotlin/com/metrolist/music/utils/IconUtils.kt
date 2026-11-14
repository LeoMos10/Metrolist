package com.metrolist.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object IconUtils {
    fun setIcon(context: Context, enabled: Boolean) {
        if (enabled) {
            setIconVariant(context, AppIconVariant.DYNAMIC)
        } else {
            setIconVariant(context, AppIconVariant.STATIC)
        }
    }

    fun setIconVariant(context: Context, variant: AppIconVariant) {
        val pm = context.packageManager
        for (entry in AppIconVariant.entries) {
            val component = ComponentName(context, "${context.packageName}.${entry.alias}")
            pm.setComponentEnabledSetting(
                component,
                if (entry == variant) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

enum class AppIconVariant(val alias: String) {
    DYNAMIC("MainActivityAlias"),
    STATIC("MainActivityStatic"),
    RED("MainActivityRed"),
    BLUE("MainActivityBlue"),
    GREEN("MainActivityGreen"),
    PURPLE("MainActivityPurple"),
    ORANGE("MainActivityOrange"),
}
