package com.kdroid.filter.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val launchable: Boolean
)

/** מצב מדיניות מחושב לכל אפליקציה — מיזוג מצב מערכת אמיתי והעדפות מקומיות. */
data class AppPolicyState(
    val info: AppInfo,
    val hidden: Boolean,
    val suspended: Boolean,
    val uninstallBlocked: Boolean,
    val netAllowed: Boolean,
    val onHome: Boolean,
    val inAppBrowserBlocked: Boolean,
    val pendingApproval: Boolean
)
