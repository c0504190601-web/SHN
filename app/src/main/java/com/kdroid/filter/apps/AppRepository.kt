package com.kdroid.filter.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.kdroid.filter.model.AppInfo

/**
 * גילוי אפליקציות מותקנות דרך PackageManager.
 */
class AppRepository(private val ctx: Context) {

    private val pm = ctx.packageManager

    fun installedApps(): List<AppInfo> {
        val launchable = launchablePackages()
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map { ai ->
            AppInfo(
                packageName = ai.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName),
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                launchable = launchable.contains(ai.packageName)
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun launchablePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    fun allPackageNames(): Set<String> =
        pm.getInstalledApplications(0).map { it.packageName }.toSet()
}
