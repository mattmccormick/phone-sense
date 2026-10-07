package ca.mattmccormick.phone_sense

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(
    val label: String,
    val icon: Drawable?,
)

fun interface AppInfoSource {
    fun resolve(packageName: String): AppInfo
}

class AppInfoResolver(private val packageManager: PackageManager) : AppInfoSource {
    override fun resolve(packageName: String): AppInfo = cache.computeIfAbsent(packageName) {
        resolveUncached(it)
    }

    private fun resolveUncached(packageName: String): AppInfo {
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val activityInfo = packageManager
            .queryIntentActivities(launcherIntent, 0)
            .firstOrNull()
            ?.activityInfo
            ?: return AppInfo(label = packageName, icon = null)

        return AppInfo(
            label = activityInfo.loadLabel(packageManager).toString(),
            icon = activityInfo.loadIcon(packageManager),
        )
    }

    private companion object {
        val cache = ConcurrentHashMap<String, AppInfo>()
    }
}
